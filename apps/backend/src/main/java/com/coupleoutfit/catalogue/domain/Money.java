package com.coupleoutfit.catalogue.domain;

import com.coupleoutfit.market.domain.MarketCodes;

public record Money(long amountMinor, String currencyCode) {
    public Money {
        if (amountMinor < 0) throw new IllegalArgumentException("Amount must be non-negative.");
        currencyCode = MarketCodes.currency(currencyCode);
    }
}
