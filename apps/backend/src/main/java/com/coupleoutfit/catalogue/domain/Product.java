package com.coupleoutfit.catalogue.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Product(UUID id, UUID retailerId, String canonicalName, String brand,
        ProductCategory category, String subcategory, ProductTarget target,
        ProductAttributes attributes, String attributeSchemaVersion, ProductStatus status,
        Instant createdAt, Instant updatedAt) {
    public Product {
        Objects.requireNonNull(id); Objects.requireNonNull(retailerId);
        canonicalName = CatalogueValidation.text(canonicalName, 300, true);
        brand = CatalogueValidation.text(brand, 160, false);
        if (subcategory != null) {
            subcategory = subcategory.strip().toUpperCase(java.util.Locale.ROOT);
            if (!subcategory.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException("Invalid subcategory.");
        }
        Objects.requireNonNull(category); Objects.requireNonNull(target);
        Objects.requireNonNull(attributes); Objects.requireNonNull(status);
        if (!"1".equals(attributeSchemaVersion)) throw new IllegalArgumentException("Unsupported attribute schema version.");
        CatalogueValidation.times(createdAt, updatedAt);
    }
}
