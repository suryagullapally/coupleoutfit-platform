package com.coupleoutfit.source.domain;

import java.time.Instant;
import java.util.*;

public record SourceAsset(UUID id, UUID sourceItemId, String storageKey, String mediaType, Long byteSize,
        Integer width, Integer height, String sha256, AssetStatus status, Instant createdAt, Instant verifiedAt, Instant deletedAt) {
    public SourceAsset {
        Objects.requireNonNull(id); Objects.requireNonNull(sourceItemId); Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
        if (!key(sourceItemId, id).equals(storageKey)) throw new IllegalArgumentException("Invalid asset key.");
        if (byteSize != null && byteSize <= 0 || width != null && width <= 0 || height != null && height <= 0) throw new IllegalArgumentException("Invalid asset measurements.");
        if (mediaType != null && !Set.of("image/jpeg","image/png","image/webp").contains(mediaType)) throw new IllegalArgumentException("Invalid asset media type.");
        if (sha256 != null && !sha256.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid asset digest.");
        if (status == AssetStatus.VERIFIED && (mediaType == null || byteSize == null || width == null || height == null || sha256 == null || verifiedAt == null)) {
            throw new IllegalArgumentException("Verified asset requires measurements.");
        }
        if (verifiedAt != null && verifiedAt.isBefore(createdAt) || deletedAt != null && deletedAt.isBefore(createdAt)) throw new IllegalArgumentException("Invalid asset timestamps.");
        if (status == AssetStatus.DELETED && deletedAt == null) throw new IllegalArgumentException("Deletion time required.");
    }
    public static String key(UUID source, UUID asset) { return "source-items/"+source+"/"+asset+"/original"; }
    public static SourceAsset pending(UUID source, Instant now) {
        UUID id=UUID.randomUUID(); return new SourceAsset(id, source, key(source,id), null,null,null,null,null,AssetStatus.PENDING,now,null,null);
    }
    public SourceAsset verified(String type, long bytes, int w, int h, String hash, Instant now) {
        if (status != AssetStatus.PENDING) throw new IllegalStateException("Asset is not pending.");
        return new SourceAsset(id,sourceItemId,storageKey,type,bytes,w,h,hash,AssetStatus.VERIFIED,createdAt,now,null);
    }
    public SourceAsset rejected() {
        if (status != AssetStatus.PENDING) throw new IllegalStateException("Asset is not pending.");
        return new SourceAsset(id,sourceItemId,storageKey,null,null,null,null,null,AssetStatus.REJECTED,createdAt,null,null);
    }
    @Override public String toString() { return "SourceAsset[id="+id+", status="+status+"]"; }
}
