package com.coupleoutfit.matching.application;
import com.coupleoutfit.matching.domain.*;
import com.coupleoutfit.catalogue.domain.*;
import java.util.UUID;
/** Current catalogue display data around an immutable scored result. */
public record MatchResultView(MatchResult result,Product product,Retailer retailer,Offer offer) {
    public record Product(UUID id,String name,String brand,ProductCategory category) {}
    public record Retailer(UUID id,String slug,String name) {}
    public record Offer(UUID offerId,Long amountMinor,String currencyCode,AvailabilityStatus availability,String imageUrl) {}
}
