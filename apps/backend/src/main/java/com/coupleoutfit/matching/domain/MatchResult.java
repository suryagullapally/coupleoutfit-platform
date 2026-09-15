package com.coupleoutfit.matching.domain;
import java.time.Instant;
import java.util.*;
public record MatchResult(UUID id,UUID matchSessionId,UUID productOfferId,int rank,CoupleMatchScore coupleMatchScore,ScoreBand scoreBand,
        CompatibilityScoreBreakdown breakdown,MatchExplanation explanation,String algorithmVersion,Instant createdAt) {
    public MatchResult {
        Objects.requireNonNull(id);Objects.requireNonNull(matchSessionId);Objects.requireNonNull(productOfferId);Objects.requireNonNull(coupleMatchScore);Objects.requireNonNull(breakdown);Objects.requireNonNull(explanation);Objects.requireNonNull(createdAt);
        if(rank<1 || scoreBand!=coupleMatchScore.band() || algorithmVersion==null || algorithmVersion.isBlank())throw new IllegalArgumentException("Invalid match result.");
    }
}
