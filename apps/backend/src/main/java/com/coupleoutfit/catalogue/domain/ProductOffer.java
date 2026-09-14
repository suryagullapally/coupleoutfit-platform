package com.coupleoutfit.catalogue.domain;

import com.coupleoutfit.retailer.domain.RetailerMarket;
import com.coupleoutfit.retailer.domain.RetailerStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ProductOffer(UUID id, UUID productId, UUID retailerMarketId, String retailerProductId,
        String title, String productUrl, String affiliateUrl, Money price, AvailabilityStatus availabilityStatus,
        String primaryImageUrl, boolean active, Instant lastSeenAt, Instant lastSyncedAt,
        Instant createdAt, Instant updatedAt) {
    public ProductOffer {
        Objects.requireNonNull(id); Objects.requireNonNull(productId); Objects.requireNonNull(retailerMarketId);
        retailerProductId = CatalogueValidation.text(retailerProductId, 200, true);
        title = CatalogueValidation.text(title, 300, true);
        productUrl = CatalogueValidation.url(productUrl, true);
        affiliateUrl = CatalogueValidation.url(affiliateUrl, false);
        primaryImageUrl = CatalogueValidation.url(primaryImageUrl, false);
        Objects.requireNonNull(availabilityStatus);
        CatalogueValidation.times(createdAt, updatedAt);
    }
    public void validateFor(Product product, RetailerMarket market) {
        if (!productId.equals(product.id()) || !retailerMarketId.equals(market.id())
                || !product.retailerId().equals(market.retailer().id())) {
            throw new IllegalArgumentException("Offer retailer ownership is inconsistent.");
        }
        if (market.status() != RetailerStatus.ACTIVE || market.retailer().status() != RetailerStatus.ACTIVE
                || !market.catalogueEnabled() || !market.country().active() || !market.defaultCurrency().active()
                || !market.locale().active()) throw new IllegalArgumentException("Catalogue market is disabled.");
        if (price != null && !price.currencyCode().equals(market.defaultCurrency().code())) {
            throw new IllegalArgumentException("Offer currency must match the retailer market.");
        }
    }
    @Override public String toString() {
        return "ProductOffer[id=" + id + ", productId=" + productId + ", retailerMarketId=" + retailerMarketId + "]";
    }
}
