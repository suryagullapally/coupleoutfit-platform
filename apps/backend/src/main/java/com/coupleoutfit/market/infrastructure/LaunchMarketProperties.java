package com.coupleoutfit.market.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("coupleoutfit.market")
public record LaunchMarketProperties(String defaultCountryCode, String defaultCurrencyCode, String defaultLocaleCode) {}
