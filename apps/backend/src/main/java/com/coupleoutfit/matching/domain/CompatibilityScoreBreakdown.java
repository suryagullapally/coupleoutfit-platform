package com.coupleoutfit.matching.domain;
import java.util.List;
public record CompatibilityScoreBreakdown(int semanticScore,int colorScore,int styleScore,int formalityScore,int patternScore,int seasonalityScore,int categoryScore) {
    public CompatibilityScoreBreakdown {for(int value:List.of(semanticScore,colorScore,styleScore,formalityScore,patternScore,seasonalityScore,categoryScore))if(value<0 || value>100)throw new IllegalArgumentException("Invalid component score.");}
    public List<Integer> values(){return List.of(semanticScore,colorScore,styleScore,formalityScore,patternScore,seasonalityScore,categoryScore);}
}
