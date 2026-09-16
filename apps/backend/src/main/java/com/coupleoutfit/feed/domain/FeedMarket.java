package com.coupleoutfit.feed.domain;
import com.coupleoutfit.market.domain.MarketContext;
import java.util.*;
public record FeedMarket(UUID retailerMarketId,UUID retailerId,MarketContext context,int minorUnitDigits,String domain) {
    public FeedMarket {Objects.requireNonNull(retailerMarketId);Objects.requireNonNull(retailerId);Objects.requireNonNull(context);Objects.requireNonNull(domain);if(minorUnitDigits<0 || minorUnitDigits>9)throw new IllegalArgumentException("Unsupported minor units.");}
}
