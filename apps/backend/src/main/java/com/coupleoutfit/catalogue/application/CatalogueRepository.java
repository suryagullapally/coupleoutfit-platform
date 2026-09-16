package com.coupleoutfit.catalogue.application;

import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import java.util.*;

/** Writes are atomic and enforce referenced market data within the transaction. */
public interface CatalogueRepository {
    Product createProduct(Product product);
    Product updateProduct(Product product);
    ProductOffer upsertOffer(ProductOffer offer);
    ProductVariant upsertVariant(ProductVariant variant);
    ProductVariant createUnidentifiedVariant(ProductVariant variant);
    Optional<Product> findProduct(UUID id);
    Optional<ProductOffer> findOffer(UUID id);
    Optional<ProductOffer> findOfferByMarketAndRetailerProductId(UUID marketId, String retailerProductId);
    List<ProductOffer> findActiveOffers(MarketContext market);
    List<ProductVariant> findVariantsByOfferId(UUID offerId);
}
