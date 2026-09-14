package com.coupleoutfit.retailer.application;

import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.retailer.domain.Retailer;
import com.coupleoutfit.retailer.domain.RetailerMarket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetailerRepository {
    Optional<Retailer> findBySlug(String slug);
    Optional<RetailerMarket> findByRetailerAndCountry(UUID retailerId, String countryCode);
    List<RetailerMarket> findActiveMarkets(MarketContext context);
}
