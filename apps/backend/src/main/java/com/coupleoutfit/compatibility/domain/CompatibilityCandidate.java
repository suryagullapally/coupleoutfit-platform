package com.coupleoutfit.compatibility.domain;
import com.coupleoutfit.catalogue.domain.*;
import java.util.*;
public record CompatibilityCandidate(UUID productId,UUID productOfferId,UUID retailerMarketId,double semanticSimilarity,
        ProductCategory category,ProductTarget target,ProductAttributes attributes,AvailabilityStatus availability,CompatibilityFeatures features) {
    public CompatibilityCandidate {
        Objects.requireNonNull(productId);Objects.requireNonNull(productOfferId);Objects.requireNonNull(retailerMarketId);Objects.requireNonNull(category);Objects.requireNonNull(target);
        Objects.requireNonNull(attributes);Objects.requireNonNull(availability);Objects.requireNonNull(features);
        if(!Double.isFinite(semanticSimilarity) || semanticSimilarity<0 || semanticSimilarity>1) throw new IllegalArgumentException("Invalid similarity.");
    }
}
