package com.coupleoutfit.catalogue.application;

import com.coupleoutfit.catalogue.domain.ProductOffer;
import com.coupleoutfit.market.application.MarketContextResolver;
import java.util.List;

public final class CatalogueQueries {
    private final CatalogueRepository repository;
    private final MarketContextResolver resolver;
    public CatalogueQueries(CatalogueRepository repository, MarketContextResolver resolver) {
        this.repository = repository;
        this.resolver = resolver;
    }
    public List<ProductOffer> activeOffers() { return repository.findActiveOffers(resolver.resolve()); }
}
