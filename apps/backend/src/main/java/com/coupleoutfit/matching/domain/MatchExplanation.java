package com.coupleoutfit.matching.domain;
import java.util.*;
public record MatchExplanation(List<String> reasons) {
    public MatchExplanation {reasons=List.copyOf(reasons);if(reasons.size()>3 || reasons.stream().anyMatch(r->r.isBlank() || r.length()>200))throw new IllegalArgumentException("Invalid explanation.");}
    public static MatchExplanation from(CompatibilityScoreBreakdown b,MatchMode mode) {
        record Reason(int score,int priority,String text){}
        var reasons=new ArrayList<Reason>();
        if(b.colorScore()>=75)reasons.add(new Reason(b.colorScore(),0,switch(mode){case EXACT->"The clothing colors are closely aligned.";case COORDINATED->"The colors coordinate naturally.";case COMPLEMENTARY->"The colors create a balanced contrast.";}));
        if(b.styleScore()>=75)reasons.add(new Reason(b.styleScore(),1,"The pieces share a similar style or style family."));
        if(b.formalityScore()>=75)reasons.add(new Reason(b.formalityScore(),2,"The pieces have similar levels of formality."));
        if(b.patternScore()>=65)reasons.add(new Reason(b.patternScore(),3,mode==MatchMode.EXACT?"The patterns are similar or balanced by a solid piece.":"The patterns have a balanced relationship."));
        if(b.seasonalityScore()>=75)reasons.add(new Reason(b.seasonalityScore(),4,"The pieces share a seasonal feel."));
        if(b.semanticScore()>=85)reasons.add(new Reason(b.semanticScore(),5,"The overall clothing styles are closely aligned."));
        return new MatchExplanation(reasons.stream().sorted(Comparator.comparingInt(Reason::score).reversed().thenComparingInt(Reason::priority)).limit(3).map(Reason::text).toList());
    }
}
