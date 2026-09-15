package com.coupleoutfit.matching.domain;
public record CoupleMatchScore(int value) {
    public CoupleMatchScore {if(value<0 || value>100)throw new IllegalArgumentException("Score must be in 0..100.");}
    public ScoreBand band(){return ScoreBand.forScore(value);}
}
