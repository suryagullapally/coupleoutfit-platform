package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.application.*;
import com.coupleoutfit.analysis.domain.*;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Repository
@Transactional(readOnly=true)
public class JpaClothingAnalysisRepository implements ClothingAnalysisRepository {
    private final EntityManager em;
    public JpaClothingAnalysisRepository(EntityManager em) {this.em=em;}
    private Optional<ClothingAnalysisEntity> entityForSource(UUID source) {
        return em.createQuery("select a from ClothingAnalysisRecord a where a.sourceItemId=:source",ClothingAnalysisEntity.class)
                .setParameter("source",source).getResultList().stream().findFirst();
    }
    private AnalysisSnapshot snapshot(ClothingAnalysisEntity entity) {
        var revision=entity.currentRevisionId==null?null:em.find(AnalysisRevisionEntity.class,entity.currentRevisionId);
        return new AnalysisSnapshot(entity.toDomain(),revision==null?null:revision.toDomain());
    }
    @Override @Transactional public StartClaim claim(UUID source,Instant now) {
        // Lock the source before the aggregate exists, preventing concurrent first-provider calls.
        if(em.createNativeQuery("SELECT id FROM source_item WHERE id=:source FOR UPDATE",UUID.class).setParameter("source",source).getResultList().isEmpty()) {
            throw new AnalysisException("ANALYSIS_NOT_FOUND");
        }
        var existing=entityForSource(source);
        if(existing.isPresent()) return new StartClaim(snapshot(existing.get()),false);
        var pending=new ClothingAnalysis(UUID.randomUUID(),source,AnalysisStatus.PENDING,null,null,now,null,null,now);
        var entity=new ClothingAnalysisEntity(pending.start(now));em.persist(entity);em.flush();
        return new StartClaim(snapshot(entity),true);
    }
    @Override public Optional<AnalysisSnapshot> findBySource(UUID source) {return entityForSource(source).map(this::snapshot);}
    @Override @Transactional public AnalysisSnapshot complete(UUID id,ClothingAnalysisProviderResult result,Instant now) {
        var entity=em.find(ClothingAnalysisEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(entity==null) throw new AnalysisException("ANALYSIS_NOT_FOUND");
        if(entity.status!=AnalysisStatus.IN_PROGRESS) throw new AnalysisException("ANALYSIS_REVISION_CONFLICT");
        var revision=new AnalysisRevision(UUID.randomUUID(),id,1,RevisionType.AI_GENERATED,null,result.attributes(),result.confidence(),"1",
                result.provider(),result.model(),result.providerRequestId(),result.promptVersion(),now);
        em.persist(new AnalysisRevisionEntity(revision));em.flush();
        entity.update(entity.toDomain().complete(revision,now));em.flush();return snapshot(entity);
    }
    @Override @Transactional public void fail(UUID id,String code,Instant now) {
        var entity=em.find(ClothingAnalysisEntity.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(entity!=null && entity.status==AnalysisStatus.IN_PROGRESS) entity.update(entity.toDomain().fail(code,now));
    }
    @Override @Transactional public AnalysisSnapshot correct(UUID source,UUID parent,ClothingAttributes attributes,Instant now) {
        var entity=entityForSource(source).orElseThrow(() -> new AnalysisException("ANALYSIS_NOT_FOUND"));
        em.refresh(entity,LockModeType.PESSIMISTIC_WRITE);
        if(entity.status!=AnalysisStatus.COMPLETED || !Objects.equals(parent,entity.currentRevisionId)) throw new AnalysisException("ANALYSIS_REVISION_CONFLICT");
        var previous=em.find(AnalysisRevisionEntity.class,parent).toDomain();
        var revision=new AnalysisRevision(UUID.randomUUID(),entity.id,Math.addExact(previous.revisionNumber(),1),RevisionType.USER_CORRECTED,parent,attributes,null,"1",null,null,null,null,now);
        em.persist(new AnalysisRevisionEntity(revision));em.flush();
        entity.update(entity.toDomain().correct(revision,now));em.flush();return snapshot(entity);
    }
}
