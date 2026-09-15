package com.coupleoutfit.matching.domain;
import java.util.*;
public final class ColorRelationshipPolicy {
    private static final Map<String,String> FAMILIES=Map.ofEntries(
        Map.entry("NAVY","BLUE"),Map.entry("BLUE","BLUE"),Map.entry("LIGHT_BLUE","BLUE"),Map.entry("RED","RED"),Map.entry("BURGUNDY","RED"),Map.entry("PINK","RED"),
        Map.entry("GREEN","GREEN"),Map.entry("OLIVE","GREEN"),Map.entry("PURPLE","PURPLE"),Map.entry("YELLOW","YELLOW"),Map.entry("GOLD","YELLOW"),Map.entry("ORANGE","ORANGE"),
        Map.entry("BLACK","BLACK"),Map.entry("WHITE","WHITE"),Map.entry("GREY","GREY"),Map.entry("SILVER","GREY"),Map.entry("BROWN","BROWN"),Map.entry("BEIGE","BROWN"),Map.entry("CREAM","WHITE"));
    private static final Set<String> NEUTRALS=Set.of("BLACK","WHITE","GREY","SILVER","NAVY","BROWN","BEIGE","CREAM");
    private static final Set<Set<String>> COMPLEMENTS=Set.of(Set.of("BLUE","ORANGE"),Set.of("RED","GREEN"),Set.of("PURPLE","YELLOW"));
    public int score(List<String> source,List<String> candidate,MatchMode mode) {
        var a=source.stream().filter(FAMILIES::containsKey).distinct().sorted().toList();var b=candidate.stream().filter(FAMILIES::containsKey).distinct().sorted().toList();
        if(a.isEmpty() || b.isEmpty())return 40;
        int sum=0;for(String color:a)sum+=b.stream().mapToInt(other->pair(color,other,mode)).max().orElse(40);
        return (int)Math.round((double)sum/a.size());
    }
    private int pair(String a,String b,MatchMode mode) {
        if(a.equals(b))return switch(mode){case EXACT->100;case COORDINATED->88;case COMPLEMENTARY->55;};
        if(FAMILIES.get(a).equals(FAMILIES.get(b)))return switch(mode){case EXACT->90;case COORDINATED->85;case COMPLEMENTARY->60;};
        if(COMPLEMENTS.contains(Set.of(FAMILIES.get(a),FAMILIES.get(b))))return switch(mode){case EXACT->30;case COORDINATED->80;case COMPLEMENTARY->100;};
        if(NEUTRALS.contains(a) || NEUTRALS.contains(b))return mode==MatchMode.EXACT?60:mode==MatchMode.COORDINATED?95:85;
        return mode==MatchMode.EXACT?35:mode==MatchMode.COORDINATED?55:45;
    }
}
