package com.coupleoutfit.catalogue.infrastructure;

import com.coupleoutfit.catalogue.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Entity(name = "ProductVariantRecord")
@Table(name = "product_variant")
class ProductVariantEntity {
    @Id UUID id;
    UUID offerId;
    @Column(length = 200) String retailerVariantId;
    @Column(length = 200) String sku;
    @Column(length = 100) String size;
    @Column(length = 100) String color;
    @Enumerated(EnumType.STRING) AvailabilityStatus availabilityStatus;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") Map<String, List<String>> attributes;
    Instant lastSyncedAt;
    Instant createdAt;
    Instant updatedAt;
    protected ProductVariantEntity() {}
    ProductVariantEntity(ProductVariant value) {
        id = value.id();
        createdAt = value.createdAt();
        update(value);
    }
    void update(ProductVariant value) {
        offerId = value.offerId();
        retailerVariantId = value.retailerVariantId();
        sku = value.sku();
        size = value.size();
        color = value.color();
        availabilityStatus = value.availabilityStatus();
        attributes = value.attributes().values();
        lastSyncedAt = value.lastSyncedAt();
        updatedAt = value.updatedAt();
    }
    ProductVariant toDomain() {
        return new ProductVariant(id, offerId, retailerVariantId, sku, size, color, availabilityStatus, new ProductAttributes(attributes), lastSyncedAt, createdAt, updatedAt);
    }
}
