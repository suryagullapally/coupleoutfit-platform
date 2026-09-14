package com.coupleoutfit.market.domain;

public record Locale(String code, String countryCode, String defaultCurrencyCode, boolean active) {
    public Locale {
        code = MarketCodes.locale(code);
        countryCode = MarketCodes.country(countryCode);
        defaultCurrencyCode = MarketCodes.currency(defaultCurrencyCode);
        String region = java.util.Locale.forLanguageTag(code).getCountry();
        if (!region.isEmpty() && !region.equals(countryCode)) throw new IllegalArgumentException("Locale region must match its country.");
    }
}
