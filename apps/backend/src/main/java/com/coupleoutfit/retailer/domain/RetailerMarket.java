package com.coupleoutfit.retailer.domain;

import com.coupleoutfit.market.domain.Country;
import com.coupleoutfit.market.domain.Currency;
import com.coupleoutfit.market.domain.Locale;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RetailerMarket(UUID id, Retailer retailer, Country country, Currency defaultCurrency,
        Locale locale, String domain, RetailerStatus status, boolean catalogueEnabled,
        boolean matchingEnabled, Instant createdAt, Instant updatedAt) {
    public RetailerMarket {
        Objects.requireNonNull(id, "Retailer market ID is required.");
        Objects.requireNonNull(retailer, "Retailer is required.");
        Objects.requireNonNull(country, "Country is required.");
        Objects.requireNonNull(defaultCurrency, "Currency is required.");
        Objects.requireNonNull(locale, "Locale is required.");
        if (!locale.countryCode().equals(country.code()) || !locale.defaultCurrencyCode().equals(defaultCurrency.code())) {
            throw new IllegalArgumentException("Retailer market must match its locale's country and default currency.");
        }
        if (domain == null) throw new IllegalArgumentException("Retailer domain is required.");
        domain = domain.strip().toLowerCase(java.util.Locale.ROOT);
        if (domain.length() > 253 || !domain.matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")) {
            throw new IllegalArgumentException("Retailer domain must be an ASCII DNS hostname without a scheme, path, port or trailing dot.");
        }
        Objects.requireNonNull(status, "Retailer market status is required.");
        Objects.requireNonNull(createdAt, "Creation time is required.");
        Objects.requireNonNull(updatedAt, "Update time is required.");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("Update time cannot precede creation time.");
    }
}
