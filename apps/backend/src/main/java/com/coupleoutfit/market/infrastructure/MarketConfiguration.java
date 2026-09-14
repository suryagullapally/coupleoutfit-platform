package com.coupleoutfit.market.infrastructure;

import com.coupleoutfit.market.application.MarketContextResolver;
import com.coupleoutfit.market.application.MarketRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LaunchMarketProperties.class)
public class MarketConfiguration {
    @Bean MarketContextResolver marketContextResolver(MarketRepository repository, LaunchMarketProperties properties) {
        return new MarketContextResolver(repository, properties.defaultCountryCode(), properties.defaultCurrencyCode(), properties.defaultLocaleCode());
    }
    @Bean ApplicationRunner validateLaunchMarket(MarketContextResolver resolver) {
        return args -> resolver.resolve();
    }
}
