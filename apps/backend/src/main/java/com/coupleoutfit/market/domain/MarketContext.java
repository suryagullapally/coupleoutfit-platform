package com.coupleoutfit.market.domain;

public record MarketContext(String countryCode, String currencyCode, String localeCode) {
    public MarketContext {
        countryCode = MarketCodes.country(countryCode);
        currencyCode = MarketCodes.currency(currencyCode);
        localeCode = MarketCodes.locale(localeCode);
    }
}
