package com.coupleoutfit.catalogue.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProductVariant(UUID id, UUID offerId, String retailerVariantId, String sku, String size,
        String color, AvailabilityStatus availabilityStatus, ProductAttributes attributes,
        Instant lastSyncedAt, Instant createdAt, Instant updatedAt) {
    public ProductVariant {
        Objects.requireNonNull(id); Objects.requireNonNull(offerId);
        retailerVariantId = CatalogueValidation.text(retailerVariantId, 200, false);
        sku = CatalogueValidation.text(sku, 200, false);
        size = CatalogueValidation.text(size, 100, false);
        color = CatalogueValidation.text(color, 100, false);
        Objects.requireNonNull(availabilityStatus); Objects.requireNonNull(attributes);
        CatalogueValidation.times(createdAt, updatedAt);
    }
}
