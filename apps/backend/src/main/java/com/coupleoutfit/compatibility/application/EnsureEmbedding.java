package com.coupleoutfit.compatibility.application;
import com.coupleoutfit.compatibility.domain.*;
import java.time.Duration;
/** Shared embedding generation, not a generic CRUD or AI framework. */
public final class EnsureEmbedding {
    private final EmbeddingRepository repository;private final EmbeddingProvider provider;private final EmbeddingModelMetadata model;private final int attempts;private final Duration backoff;
    public EnsureEmbedding(EmbeddingRepository repository,EmbeddingProvider provider,EmbeddingModelMetadata model,int attempts,Duration backoff) {
        this.repository=repository;this.provider=provider;this.model=model;this.attempts=attempts;this.backoff=backoff;
        if(attempts<1 || attempts>3 || backoff.isNegative() || backoff.compareTo(Duration.ofSeconds(2))>0) throw new IllegalArgumentException("Invalid embedding retry limits.");
    }
    public EmbeddingData ensure(EmbeddingSource source,EmbeddingDescriptor descriptor) {
        var claim=repository.claim(source,model,descriptor);
        if(!claim.acquired()) {if(claim.existing()!=null)return claim.existing();throw new EmbeddingException("EMBEDDING_NOT_AVAILABLE");}
        try {
            var result=call(descriptor);
            if(!model.equals(result.model()) || result.vector().values().size()!=model.dimensions()) throw new EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
            return repository.complete(claim.id(),source,model,descriptor,result);
        } catch(RuntimeException failure) {repository.abandon(claim.id(),source);if(failure instanceof EmbeddingException e)throw e;throw new EmbeddingException("EMBEDDING_PROVIDER_UNAVAILABLE");}
    }
    private EmbeddingProvider.Result call(EmbeddingDescriptor descriptor) {
        for(int attempt=1;;attempt++) {
            try {return provider.embed(descriptor);}catch(EmbeddingException e) {
                if(!e.transientFailure() || attempt>=attempts)throw e;
                try {Thread.sleep(backoff.multipliedBy(attempt));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new EmbeddingException("EMBEDDING_PROVIDER_UNAVAILABLE");}
            }
        }
    }
}
