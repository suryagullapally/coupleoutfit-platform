package com.coupleoutfit.compatibility.domain;
import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.catalogue.domain.*;
import java.util.*;
public record CompatibilityRequest(UUID analysisRevisionId,MarketContext marketContext,ProductTarget target,ProductCategory sourceCategory,int candidateLimit) {
    public CompatibilityRequest {
        Objects.requireNonNull(analysisRevisionId);Objects.requireNonNull(marketContext);Objects.requireNonNull(target);Objects.requireNonNull(sourceCategory);
        if(candidateLimit<1 || candidateLimit>200) throw new IllegalArgumentException("Candidate limit must be between 1 and 200.");
    }
}
