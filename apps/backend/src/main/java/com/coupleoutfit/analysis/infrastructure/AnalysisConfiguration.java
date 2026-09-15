package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.application.*;
import com.coupleoutfit.source.application.SourceItemRepository;
import com.coupleoutfit.source.application.UploadPolicy;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.*;

@Configuration(proxyBeanMethods=false)
public class AnalysisConfiguration {
    @Bean @ConditionalOnMissingBean(ClothingAnalysisPort.class)
    ClothingAnalysisPort clothingAnalysisProvider(@Value("${coupleoutfit.ai.clothing-analysis.provider:disabled}") String provider,
            @Value("${coupleoutfit.ai.clothing-analysis.api-key:}") String key,
            @Value("${coupleoutfit.ai.clothing-analysis.model:}") String model,
            @Value("${coupleoutfit.ai.clothing-analysis.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${coupleoutfit.ai.clothing-analysis.request-timeout:PT20S}") Duration requestTimeout,
            @Value("${coupleoutfit.ai.clothing-analysis.image-detail:low}") String detail,
            @Value("${coupleoutfit.ai.clothing-analysis.max-output-tokens:2048}") long maxOutputTokens) throws java.io.IOException {
        if(!Set.of("disabled","openai").contains(provider)) throw new IllegalArgumentException("Unsupported analysis provider.");
        if(connectTimeout.isZero() || connectTimeout.isNegative() || connectTimeout.compareTo(Duration.ofSeconds(10))>0
                || requestTimeout.isZero() || requestTimeout.isNegative() || requestTimeout.compareTo(Duration.ofSeconds(30))>0
                || !Set.of("low","high","auto").contains(detail) || maxOutputTokens<256 || maxOutputTokens>4096) throw new IllegalArgumentException("Invalid provider limits.");
        if(provider.equals("disabled") || key.isBlank() || model.isBlank()) return image -> {throw new AnalysisException("ANALYSIS_PROVIDER_UNAVAILABLE");};
        if(!model.matches("[A-Za-z0-9._:-]{1,120}")) throw new IllegalArgumentException("Invalid configured model.");
        var client=OpenAIOkHttpClient.builder().apiKey(key).maxRetries(0).followRedirects(false).logLevel(LogLevel.OFF)
                .timeout(Timeout.builder().connect(connectTimeout).read(requestTimeout).write(requestTimeout).request(requestTimeout).build()).build();
        String prompt;
        Map<String,Object> schema;
        try(var p=getClass().getResourceAsStream("/analysis/clothing-analysis-v1.txt");var s=getClass().getResourceAsStream("/analysis/clothing-analysis-schema-v1.json")) {
            prompt=new String(Objects.requireNonNull(p).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            schema=JsonMapper.builder().build().readValue(Objects.requireNonNull(s),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});
        }
        return new OpenAiClothingAnalysisAdapter(client,model,prompt,"clothing-analysis-v1",schema,detail,maxOutputTokens);
    }
    @Bean SourceImageAccessPort analysisImageAccess(SourceAssetStorage storage,UploadPolicy policy) { return new StoredSourceImageAccess(storage,policy); }
    @Bean StartClothingAnalysis startClothingAnalysis(SourceItemRepository sources,ClothingAnalysisRepository repository,SourceImageAccessPort images,
            ClothingAnalysisPort provider,Clock sourceClock,AnalysisMetrics metrics,@Value("${coupleoutfit.ai.clothing-analysis.max-attempts:2}") int attempts,
            @Value("${coupleoutfit.ai.clothing-analysis.retry-backoff:PT0.25S}") Duration backoff) {
        return new StartClothingAnalysis(sources,repository,images,provider,sourceClock,attempts,backoff,metrics);
    }
    @Bean ReviewClothingAnalysis reviewClothingAnalysis(SourceItemRepository sources,ClothingAnalysisRepository repository,Clock sourceClock) {
        return new ReviewClothingAnalysis(sources,repository,sourceClock);
    }
}
