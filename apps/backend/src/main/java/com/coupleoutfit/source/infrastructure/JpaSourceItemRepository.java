package com.coupleoutfit.source.infrastructure;

import com.coupleoutfit.source.application.*;
import com.coupleoutfit.source.domain.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;

@Repository
@Transactional(readOnly=true)
public class JpaSourceItemRepository implements SourceItemRepository {
    private final EntityManager em;
    private final Duration verificationLease;
    public JpaSourceItemRepository(EntityManager em, @Value("${coupleoutfit.source.upload.verification-lease:PT2M}") Duration verificationLease) {
        this.em=em; this.verificationLease=verificationLease;
        if(verificationLease.compareTo(Duration.ofSeconds(30))<0) throw new IllegalArgumentException("Verification lease is too short.");
    }
    @Override @Transactional public SourceSnapshot create(SourceItem item, SourceAsset asset, SourceUrl url) {
        em.persist(new SourceItemEntity(item));
        if(asset!=null) em.persist(new SourceAssetEntity(asset));
        if(url!=null) em.persist(new SourceUrlEntity(url));
        em.flush(); return new SourceSnapshot(item,asset,url);
    }
    private SourceItemEntity owned(UUID id,String owner,boolean lock) {
        var query=em.createQuery("select s from SourceItemRecord s where s.id=:id and s.ownerSessionId=:owner",SourceItemEntity.class)
                .setParameter("id",id).setParameter("owner",owner);
        if(lock) query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultList().stream().findFirst().orElseThrow(() -> new SourceException("SOURCE_NOT_FOUND"));
    }
    private SourceSnapshot snapshot(SourceItemEntity item) {
        var asset=em.createQuery("select a from SourceAssetRecord a where a.sourceItemId=:id",SourceAssetEntity.class)
                .setParameter("id",item.id).getResultList().stream().findFirst().map(SourceAssetEntity::toDomain).orElse(null);
        var url=em.find(SourceUrlEntity.class,item.id);
        return new SourceSnapshot(item.toDomain(),asset,url==null?null:url.toDomain());
    }
    @Override public SourceSnapshot findOwned(UUID id,String owner) { return snapshot(owned(id,owner,false)); }
    @Override @Transactional public SourceSnapshot startFallback(UUID id,String owner,Instant now) {
        var entity=owned(id,owner,true);
        if(entity.status!=SourceStatus.IMAGE_UPLOAD_REQUIRED) throw new SourceException("SOURCE_INVALID_STATE");
        entity.update(entity.toDomain().transition(SourceStatus.AWAITING_UPLOAD,now));
        em.persist(new SourceAssetEntity(SourceAsset.pending(id,now))); em.flush(); return snapshot(entity);
    }
    @Override @Transactional public SourceSnapshot beginVerification(UUID id,UUID assetId,String owner,Instant now) {
        now=now.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var entity=owned(id,owner,true); var current=snapshot(entity);
        if(current.asset()==null || !current.asset().id().equals(assetId)) throw new SourceException("SOURCE_ASSET_NOT_FOUND");
        if(entity.status==SourceStatus.READY_FOR_ANALYSIS && current.asset().status()==AssetStatus.VERIFIED) return current;
        if(entity.status==SourceStatus.UPLOAD_VERIFYING && entity.updatedAt.plus(verificationLease).isBefore(now)) {
            // Immutable object permits retry after an interrupted verifier's lease expires.
            entity.updatedAt=now; return snapshot(entity);
        }
        if(entity.status!=SourceStatus.AWAITING_UPLOAD) throw new SourceException("SOURCE_INVALID_STATE");
        entity.update(entity.toDomain().transition(SourceStatus.UPLOAD_VERIFYING,now)); return snapshot(entity);
    }
    @Override @Transactional public SourceSnapshot finishVerification(UUID id,SourceAsset asset,boolean success,Instant claim,Instant now) {
        var entity=em.find(SourceItemEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(entity==null) throw new SourceException("SOURCE_NOT_FOUND");
        var current=snapshot(entity);
        if(current.asset()==null || !current.asset().id().equals(asset.id()) || !id.equals(asset.sourceItemId())) throw new SourceException("SOURCE_ASSET_NOT_FOUND");
        if(entity.status==SourceStatus.READY_FOR_ANALYSIS || entity.status==SourceStatus.FAILED) return current;
        if(entity.status!=SourceStatus.UPLOAD_VERIFYING || !entity.updatedAt.equals(claim)) throw new SourceException("SOURCE_INVALID_STATE");
        if(success!=(asset.status()==AssetStatus.VERIFIED)) throw new SourceException("SOURCE_ASSET_INVALID");
        em.find(SourceAssetEntity.class,asset.id()).update(asset);
        entity.update(entity.toDomain().transition(success?SourceStatus.READY_FOR_ANALYSIS:SourceStatus.FAILED,now));
        return snapshot(entity);
    }
    @Override @Transactional public void releaseVerification(UUID id, String owner, Instant claim, Instant now) {
        var entity=owned(id,owner,true);
        if(entity.status==SourceStatus.UPLOAD_VERIFYING && entity.updatedAt.equals(claim)) entity.update(entity.toDomain().transition(SourceStatus.AWAITING_UPLOAD,now));
    }
}
