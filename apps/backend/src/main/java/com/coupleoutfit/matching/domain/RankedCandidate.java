package com.coupleoutfit.matching.domain;
import com.coupleoutfit.compatibility.domain.CompatibilityCandidate;
public record RankedCandidate(CompatibilityCandidate candidate,CoupleMatchScore score,CompatibilityScoreBreakdown breakdown,MatchExplanation explanation) {}
