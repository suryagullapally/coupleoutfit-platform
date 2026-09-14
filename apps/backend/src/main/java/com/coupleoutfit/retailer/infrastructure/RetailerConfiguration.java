package com.coupleoutfit.retailer.infrastructure;

import com.coupleoutfit.market.application.MarketContextResolver;
import com.coupleoutfit.retailer.application.RetailerRepository;
import com.coupleoutfit.retailer.application.RetailerQueries;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RetailerConfiguration {
    @Bean RetailerQueries retailerQueries(RetailerRepository repository, MarketContextResolver resolver) {
        return new RetailerQueries(repository, resolver);
    }
}
