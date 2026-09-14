package com.coupleoutfit.source.infrastructure;

import com.coupleoutfit.source.application.*;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import com.coupleoutfit.market.application.MarketContextResolver;
import com.coupleoutfit.catalogue.application.CatalogueRepository;
import com.coupleoutfit.retailer.application.RetailerRepository;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;

@Configuration(proxyBeanMethods=false)
public class SourceConfiguration {
    @Bean Clock sourceClock() { return Clock.systemUTC(); }
    @Bean UploadPolicy uploadPolicy(@Value("${coupleoutfit.source.upload.max-bytes:10485760}") long bytes,
            @Value("${coupleoutfit.source.upload.max-pixels:25000000}") long pixels,
            @Value("${coupleoutfit.source.upload.max-dimension:10000}") int dimension,
            @Value("${coupleoutfit.source.upload.allowed-media-types:image/jpeg,image/png,image/webp}") String mediaTypes,
            @Value("${coupleoutfit.source.upload.authorization-ttl:PT15M}") Duration ttl) {
        return new UploadPolicy(bytes,pixels,dimension,Set.of(mediaTypes.split(",")),ttl);
    }
    @Bean ImageVerifier imageVerifier(SourceAssetStorage storage, UploadPolicy policy,
            @Value("${coupleoutfit.source.upload.max-concurrent-verifications:2}") int concurrency) {
        return new StoredImageVerifier(storage,policy,concurrency);
    }
    @Bean UrlResolverPort sourceUrlResolver(CatalogueRepository catalogue, RetailerRepository retailers, Clock sourceClock) {
        return new CatalogueOfferUrlResolver(catalogue,retailers,sourceClock);
    }
    @Bean CreateSourceItem createSourceItem(SourceItemRepository repository, MarketContextResolver resolver,
            UrlResolverPort urls, SourceAssetStorage storage, Clock sourceClock) {
        return new CreateSourceItem(repository,resolver,urls,storage,sourceClock);
    }
    @Bean CompleteSourceUpload completeSourceUpload(SourceItemRepository repository,ImageVerifier verifier,SourceAssetStorage storage,Clock sourceClock) {
        return new CompleteSourceUpload(repository,verifier,storage,sourceClock);
    }
}
