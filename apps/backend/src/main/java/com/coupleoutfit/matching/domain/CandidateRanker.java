package com.coupleoutfit.matching.domain;
import com.coupleoutfit.compatibility.domain.CompatibilityCandidate;
import com.coupleoutfit.catalogue.domain.AvailabilityStatus;
import java.util.*;
public final class CandidateRanker {
    private final CompatibilityScorer scorer=new CompatibilityScorer();
    public List<RankedCandidate> rank(List<CompatibilityCandidate> candidates,MatchMode mode,int threshold,int limit) {
        if(threshold<60 || threshold>100 || limit<1 || limit>50)throw new IllegalArgumentException("Invalid ranking limits.");
        var strategy=RankingAlgorithm.strategy(mode);
        return candidates.stream().map(c->{var b=scorer.score(c.features(),strategy);return new RankedCandidate(c,RankingAlgorithm.score(b,strategy),b,MatchExplanation.from(b,mode));})
                .filter(c->c.score().value()>=threshold).sorted(Comparator.comparingInt((RankedCandidate c)->c.score().value()).reversed()
                    .thenComparing(Comparator.comparingDouble((RankedCandidate c)->c.candidate().semanticSimilarity()).reversed())
                    .thenComparingInt(c->c.candidate().availability()==AvailabilityStatus.IN_STOCK?0:1).thenComparing(c->c.candidate().productOfferId().toString()))
                .limit(limit).toList();
    }
}
