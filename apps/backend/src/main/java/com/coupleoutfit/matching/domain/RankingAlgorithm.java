package com.coupleoutfit.matching.domain;
import java.util.*;
import java.math.*;
public final class RankingAlgorithm {
    public static final String VERSION="couple-match-v1";
    private static final Map<MatchMode,List<Integer>> WEIGHTS=Map.of(
        MatchMode.EXACT,List.of(25,25,15,15,10,5,5),
        MatchMode.COORDINATED,List.of(15,25,20,20,10,5,5),
        MatchMode.COMPLEMENTARY,List.of(10,30,20,20,10,5,5));
    private RankingAlgorithm() {}
    public static List<Integer> weights(MatchMode mode){return WEIGHTS.get(Objects.requireNonNull(mode));}
    public static CoupleMatchScore score(CompatibilityScoreBreakdown breakdown,RankingStrategy strategy) {
        var weights=weights(strategy.mode());var values=breakdown.values();int sum=0;
        for(int i=0;i<weights.size();i++)sum+=weights.get(i)*values.get(i);
        return new CoupleMatchScore(BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(100),0,RoundingMode.HALF_UP).intValueExact());
    }
    public static int normalize(double similarity) {
        if(!Double.isFinite(similarity))throw new IllegalArgumentException("Invalid similarity.");
        return BigDecimal.valueOf(Math.clamp(similarity,0,1)).multiply(BigDecimal.valueOf(100)).setScale(0,RoundingMode.HALF_UP).intValueExact();
    }
    public static RankingStrategy strategy(MatchMode mode){return switch(mode){case EXACT->new ExactRankingStrategy();case COORDINATED->new CoordinatedRankingStrategy();case COMPLEMENTARY->new ComplementaryRankingStrategy();};}
}
