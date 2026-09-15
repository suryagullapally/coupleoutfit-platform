package com.coupleoutfit.compatibility.application;
import com.coupleoutfit.compatibility.domain.*;
public interface EmbeddingProvider {
    record Result(EmbeddingVector vector,EmbeddingModelMetadata model,String providerRequestId) {
        public Result { java.util.Objects.requireNonNull(vector);java.util.Objects.requireNonNull(model);
            if(vector.values().size()!=model.dimensions()) throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
            if(providerRequestId!=null && !providerRequestId.matches("[A-Za-z0-9_-]{1,200}")) throw new EmbeddingException("EMBEDDING_INVALID_RESPONSE");
        }
    }
    Result embed(EmbeddingDescriptor descriptor);
}
