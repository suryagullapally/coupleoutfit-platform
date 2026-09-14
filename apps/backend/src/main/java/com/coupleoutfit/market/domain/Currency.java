package com.coupleoutfit.market.domain;

public record Currency(String code, int minorUnitDigits, boolean active) {
    public Currency {
        code = MarketCodes.currency(code);
        if (minorUnitDigits < 0 || minorUnitDigits > Short.MAX_VALUE) throw new IllegalArgumentException("Currency precision must be a nonnegative small integer.");
    }
}
