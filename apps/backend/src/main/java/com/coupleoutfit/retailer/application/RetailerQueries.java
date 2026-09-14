package com.coupleoutfit.retailer.application;

import com.coupleoutfit.market.application.MarketContextResolver;
import com.coupleoutfit.market.domain.MarketCodes;
import com.coupleoutfit.retailer.domain.Retailer;
import com.coupleoutfit.retailer.domain.RetailerMarket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class RetailerQueries {
    private final RetailerRepository repository;
    private final MarketContextResolver resolver;
    public RetailerQueries(RetailerRepository repository, MarketContextResolver resolver) {
        this.repository = repository;
        this.resolver = resolver;
    }
    public List<RetailerMarket> activeMarkets() { return repository.findActiveMarkets(resolver.resolve()); }
    public Optional<Retailer> bySlug(String slug) {
        if (slug == null || slug.isBlank()) throw new IllegalArgumentException("Retailer slug is required.");
        return repository.findBySlug(slug.strip().toLowerCase(java.util.Locale.ROOT));
    }
    public Optional<RetailerMarket> byRetailerAndCountry(UUID retailerId, String countryCode) {
        return repository.findByRetailerAndCountry(java.util.Objects.requireNonNull(retailerId), MarketCodes.country(countryCode));
    }
}
