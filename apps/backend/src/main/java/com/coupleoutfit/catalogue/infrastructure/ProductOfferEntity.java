package com.coupleoutfit.catalogue.infrastructure;

import com.coupleoutfit.catalogue.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Entity(name = "ProductOfferRecord")
@Table(name = "product_offer")
class ProductOfferEntity {
    @Id UUID id;
    UUID productId;
    UUID retailerMarketId;
    UUID retailerId;
    @Column(length = 200) String retailerProductId;
    @Column(length = 300) String title;
    @Column(length = 4096) String productUrl;
    @Column(length = 4096) String affiliateUrl;
    Long amountMinor;
    @Column(length = 3) String currencyCode;
    @Enumerated(EnumType.STRING) AvailabilityStatus availabilityStatus;
    @Column(length = 4096) String primaryImageUrl;
    boolean active;
    Instant lastSeenAt;
    Instant lastSyncedAt;
    Instant createdAt;
    Instant updatedAt;
    protected ProductOfferEntity() {}
    ProductOfferEntity(ProductOffer value) {
        id = value.id();
        createdAt = value.createdAt();
        update(value);
    }
    void update(ProductOffer value) {
        productId = value.productId();
        retailerMarketId = value.retailerMarketId();
        retailerProductId = value.retailerProductId();
        title = value.title();
        productUrl = value.productUrl();
        affiliateUrl = value.affiliateUrl();
        amountMinor = value.price() == null ? null : value.price().amountMinor();
        currencyCode = value.price() == null ? null : value.price().currencyCode();
        availabilityStatus = value.availabilityStatus();
        primaryImageUrl = value.primaryImageUrl();
        active = value.active();
        lastSeenAt = value.lastSeenAt();
        lastSyncedAt = value.lastSyncedAt();
        updatedAt = value.updatedAt();
    }
    ProductOffer toDomain() {
        return new ProductOffer(id, productId, retailerMarketId, retailerProductId, title, productUrl, affiliateUrl, amountMinor == null ? null : new Money(amountMinor, currencyCode), availabilityStatus, primaryImageUrl, active, lastSeenAt, lastSyncedAt, createdAt, updatedAt);
    }
}
