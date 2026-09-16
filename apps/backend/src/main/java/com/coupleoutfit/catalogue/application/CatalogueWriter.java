package com.coupleoutfit.catalogue.application;

import com.coupleoutfit.catalogue.domain.*;

/** Internal write boundary for future adapters; persistence owns atomic reference validation. */
public final class CatalogueWriter {
    private final CatalogueRepository repository;
    public CatalogueWriter(CatalogueRepository repository) { this.repository = repository; }
    public Product createProduct(Product product) { return repository.createProduct(product); }
    public Product updateProduct(Product product) { return repository.updateProduct(product); }
    public ProductOffer upsertOffer(ProductOffer offer) { return repository.upsertOffer(offer); }
    public ProductVariant upsertVariant(ProductVariant variant) { return repository.upsertVariant(variant); }
    public ProductVariant createUnidentifiedVariant(ProductVariant variant) {
        return repository.createUnidentifiedVariant(variant);
    }
}
