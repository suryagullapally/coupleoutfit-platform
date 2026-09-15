package com.coupleoutfit.compatibility.application;
import com.coupleoutfit.compatibility.domain.*;
import java.util.UUID;
public interface EmbeddingRepository {
    record Claim(UUID id,boolean acquired,EmbeddingData existing) {}
    Claim claim(EmbeddingSource source,EmbeddingModelMetadata model,EmbeddingDescriptor descriptor);
    EmbeddingData complete(UUID claimId,EmbeddingSource source,EmbeddingModelMetadata model,EmbeddingDescriptor descriptor,EmbeddingProvider.Result result);
    void abandon(UUID claimId,EmbeddingSource source);
}
