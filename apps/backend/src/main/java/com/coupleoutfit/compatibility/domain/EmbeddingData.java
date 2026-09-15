package com.coupleoutfit.compatibility.domain;
import java.time.Instant;
import java.util.Objects;
public record EmbeddingData(EmbeddingModelMetadata model,EmbeddingType type,String descriptorVersion,String sourceVersion,
        String descriptorHash,EmbeddingVector vector,String providerRequestId,Instant createdAt) {
    public EmbeddingData {
        Objects.requireNonNull(model);Objects.requireNonNull(type);Objects.requireNonNull(vector);Objects.requireNonNull(createdAt);
        if(vector.values().size()!=model.dimensions() || descriptorVersion==null || !descriptorVersion.matches("[a-z0-9-]{1,80}")
                || sourceVersion==null || sourceVersion.isBlank() || sourceVersion.length()>160 || descriptorHash==null || !descriptorHash.matches("[a-f0-9]{64}")
                || providerRequestId!=null && !providerRequestId.matches("[A-Za-z0-9_-]{1,200}")) throw new IllegalArgumentException("Invalid embedding data.");
    }
}
