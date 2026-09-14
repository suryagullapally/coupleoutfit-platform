package com.coupleoutfit.market.domain;

public final class MarketCodes {
    private MarketCodes() {}
    public static String country(String code) {
        if (code == null || !code.matches("[A-Z]{2}")) throw new IllegalArgumentException("Country code must be two uppercase letters.");
        return code;
    }
    public static String currency(String code) {
        if (code == null || !code.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency code must be three uppercase letters.");
        java.util.Currency.getInstance(code);
        return code;
    }
    public static String locale(String code) {
        if (code == null || code.isBlank() || code.length() > 64) throw new IllegalArgumentException("Locale code is required.");
        try {
            var locale = new java.util.Locale.Builder().setLanguageTag(code).build();
            if (locale.getLanguage().isEmpty() || !locale.toLanguageTag().equals(code)) throw new IllegalArgumentException("Locale code must be canonical BCP 47.");
        } catch (java.util.IllformedLocaleException ex) {
            throw new IllegalArgumentException("Locale code must be canonical BCP 47.");
        }
        return code;
    }
}
