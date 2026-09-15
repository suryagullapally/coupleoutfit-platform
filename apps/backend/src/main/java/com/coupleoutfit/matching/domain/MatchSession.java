package com.coupleoutfit.matching.domain;
import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.catalogue.domain.ProductTarget;
import com.coupleoutfit.analysis.domain.ClothingAttributes;
import java.time.Instant;
import java.util.*;
public record MatchSession(UUID id,UUID sourceItemId,UUID analysisRevisionId,MarketContext market,ProductTarget partnerTarget,MatchMode matchMode,
        boolean sameRetailerOnly,UUID sourceRetailerMarketId,MatchStatus status,String algorithmVersion,String failureCode,
        int candidateLimit,int resultLimit,int minimumScore,ClothingAttributes sourceAttributes,Instant createdAt,Instant startedAt,Instant completedAt,Instant updatedAt) {
    public MatchSession {
        Objects.requireNonNull(id);Objects.requireNonNull(sourceItemId);Objects.requireNonNull(analysisRevisionId);Objects.requireNonNull(market);Objects.requireNonNull(partnerTarget);Objects.requireNonNull(matchMode);Objects.requireNonNull(status);
        Objects.requireNonNull(sourceAttributes);Objects.requireNonNull(createdAt);Objects.requireNonNull(updatedAt);
        if(sameRetailerOnly && sourceRetailerMarketId==null || algorithmVersion==null || algorithmVersion.isBlank() || candidateLimit<1 || candidateLimit>200 || resultLimit<1 || resultLimit>50 || resultLimit>candidateLimit || minimumScore<60 || minimumScore>100)
            throw new IllegalArgumentException("Invalid match session configuration.");
        if(updatedAt.isBefore(createdAt) || status!=MatchStatus.CREATED && startedAt==null || status==MatchStatus.COMPLETED && completedAt==null || status==MatchStatus.FAILED && failureCode==null)
            throw new IllegalArgumentException("Invalid match state.");
    }
}
