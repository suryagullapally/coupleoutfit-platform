package com.coupleoutfit.compatibility.infrastructure;
import com.coupleoutfit.compatibility.application.*;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.catalogue.application.CatalogueRepository;
import com.coupleoutfit.source.application.SourceItemRepository;
import com.coupleoutfit.analysis.application.ClothingAnalysisRepository;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import java.time.Duration;
@Configuration(proxyBeanMethods=false)
public class EmbeddingConfiguration {
    @Bean EmbeddingModelMetadata embeddingModel(@Value("${coupleoutfit.ai.embeddings.model}")String model,@Value("${coupleoutfit.ai.embeddings.dimensions}")int dimensions) {
        if(dimensions!=VectorStorage.DIMENSIONS)throw new IllegalArgumentException("Embedding dimensions must match the migrated vector column.");
        return new EmbeddingModelMetadata("OPENAI",model,dimensions);
    }
    @Bean @ConditionalOnMissingBean(EmbeddingProvider.class)
    EmbeddingProvider embeddingProvider(EmbeddingModelMetadata model,@Value("${coupleoutfit.ai.embeddings.provider}")String provider,
            @Value("${coupleoutfit.ai.embeddings.api-key:}")String key,@Value("${coupleoutfit.ai.embeddings.connect-timeout}")Duration connect,
            @Value("${coupleoutfit.ai.embeddings.request-timeout}")Duration request) {
        if(!java.util.Set.of("disabled","openai").contains(provider) || connect.isNegative() || connect.isZero() || connect.compareTo(Duration.ofSeconds(10))>0
                || request.isNegative() || request.isZero() || request.compareTo(Duration.ofSeconds(30))>0)throw new IllegalArgumentException("Invalid embedding configuration.");
        if(provider.equals("disabled") || key.isBlank())return descriptor -> {throw new EmbeddingException("EMBEDDING_PROVIDER_UNAVAILABLE");};
        var client=OpenAIOkHttpClient.builder().apiKey(key).maxRetries(0).followRedirects(false).logLevel(LogLevel.OFF)
                .timeout(Timeout.builder().connect(connect).read(request).write(request).request(request).build()).build();
        return new OpenAiEmbeddingProvider(client,model);
    }
    @Bean EmbeddingDescriptorFactory embeddingDescriptors(){return new EmbeddingDescriptorFactory();}
    @Bean EnsureEmbedding ensureEmbedding(EmbeddingRepository repository,EmbeddingProvider provider,EmbeddingModelMetadata model,
            @Value("${coupleoutfit.ai.embeddings.max-attempts}")int attempts,@Value("${coupleoutfit.ai.embeddings.retry-backoff}")Duration backoff) {
        return new EnsureEmbedding(repository,provider,model,attempts,backoff);
    }
    @Bean EnsureProductEmbedding ensureProductEmbedding(CatalogueRepository catalogue,EnsureEmbedding ensure,EmbeddingDescriptorFactory descriptors){return new EnsureProductEmbedding(catalogue,ensure,descriptors);}
    @Bean EnsureAnalysisEmbedding ensureAnalysisEmbedding(EnsureEmbedding ensure,EmbeddingDescriptorFactory descriptors){return new EnsureAnalysisEmbedding(ensure,descriptors);}
    @Bean RetrieveCompatibilityCandidates retrieveCompatibilityCandidates(SourceItemRepository sources,ClothingAnalysisRepository analyses,EnsureAnalysisEmbedding embeddings,CandidateRetrievalPort candidates) {
        return new RetrieveCompatibilityCandidates(sources,analyses,embeddings,candidates);
    }
}
