package com.coupleoutfit.catalogue.infrastructure;

import com.coupleoutfit.catalogue.application.*;
import com.coupleoutfit.market.application.MarketContextResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CatalogueConfiguration {
    @Bean CatalogueWriter catalogueWriter(CatalogueRepository repository) {
        return new CatalogueWriter(repository);
    }
    @Bean CatalogueQueries catalogueQueries(CatalogueRepository repository, MarketContextResolver resolver) {
        return new CatalogueQueries(repository, resolver);
    }
}
