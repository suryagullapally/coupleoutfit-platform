package com.coupleoutfit.source.infrastructure;

import com.coupleoutfit.source.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name="SourceAssetRecord")
@Table(name="source_asset")
class SourceAssetEntity {
    @Id UUID id;
    UUID sourceItemId;
    String storageKey;
    String mediaType;
    Long byteSize;
    Integer width;
    Integer height;
    @Column(length=64) String sha256;
    @Enumerated(EnumType.STRING) AssetStatus status;
    Instant createdAt;
    Instant verifiedAt;
    Instant deletedAt;
    protected SourceAssetEntity() {}
    SourceAssetEntity(SourceAsset value) { update(value); }
    void update(SourceAsset value) {
        id=value.id();
        sourceItemId=value.sourceItemId();
        storageKey=value.storageKey();
        mediaType=value.mediaType();
        byteSize=value.byteSize();
        width=value.width();
        height=value.height();
        sha256=value.sha256();
        status=value.status();
        createdAt=value.createdAt();
        verifiedAt=value.verifiedAt();
        deletedAt=value.deletedAt();
    }
    SourceAsset toDomain() { return new SourceAsset(id,sourceItemId,storageKey,mediaType,byteSize,width,height,sha256,status,createdAt,verifiedAt,deletedAt); }
}
