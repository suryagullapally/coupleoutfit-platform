package com.coupleoutfit.market.domain;

public record Country(String code, String name, boolean active) {
    public Country {
        code = MarketCodes.country(code);
        if (name == null || name.isBlank() || name.length() > 120) throw new IllegalArgumentException("Country name is required and limited to 120 characters.");
        name = name.strip();
    }
}
