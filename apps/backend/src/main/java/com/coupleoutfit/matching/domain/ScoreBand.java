package com.coupleoutfit.matching.domain;
public enum ScoreBand {
    EXCELLENT,GREAT,GOOD,POSSIBLE,BELOW_THRESHOLD;
    public static ScoreBand forScore(int score) {
        if(score<0 || score>100)throw new IllegalArgumentException("Invalid score.");
        return score>=90?EXCELLENT:score>=80?GREAT:score>=70?GOOD:score>=60?POSSIBLE:BELOW_THRESHOLD;
    }
}
