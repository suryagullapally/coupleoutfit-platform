package com.coupleoutfit.matching.domain;
import com.coupleoutfit.compatibility.domain.CompatibilityFeatures;
import com.coupleoutfit.analysis.domain.ClothingVocabulary;
import com.coupleoutfit.catalogue.domain.ProductCategory;
import java.util.*;
public final class CompatibilityScorer {
    private final ColorRelationshipPolicy colors=new ColorRelationshipPolicy();
    private static final Map<String,Integer> FORMALITY=Map.of("SPORTY",0,"CASUAL",1,"SMART_CASUAL",2,"SMART",3,"FORMAL",4,"EVENING",4);
    private static final List<Set<String>> STYLE_FAMILIES=List.of(Set.of("CLASSIC","MINIMAL","ELEGANT","TAILORED","PREPPY","CONTEMPORARY"),Set.of("SPORTY","STREETWEAR","UTILITY","RELAXED"),Set.of("ROMANTIC","BOHEMIAN","VINTAGE"),Set.of("ELEGANT","EVENING","FESTIVE"));
    public CompatibilityScoreBreakdown score(CompatibilityFeatures f,RankingStrategy strategy) {
        return new CompatibilityScoreBreakdown(RankingAlgorithm.normalize(f.semanticSimilarity()),colors.score(f.sourceColors(),f.candidateColors(),strategy.mode()),
                style(f.sourceStyleTags(),f.candidateStyleTags()),formality(f.sourceFormality(),f.candidateFormality()),pattern(f.sourcePattern(),f.candidatePattern(),strategy.mode()),
                seasons(f.sourceSeasonality(),f.candidateSeasonality()),category(f.sourceCategory(),f.candidateCategory()));
    }
    public int style(List<String> source,List<String> candidate) {
        var a=known(source,ClothingVocabulary.STYLES);var b=known(candidate,ClothingVocabulary.STYLES);if(a.isEmpty() || b.isEmpty())return 40;
        var common=new HashSet<>(a);common.retainAll(b);
        if(!common.isEmpty()){var union=new HashSet<>(a);union.addAll(b);return (int)Math.round(100.0*common.size()/union.size());}
        for(var family:STYLE_FAMILIES)if(!Collections.disjoint(a,family) && !Collections.disjoint(b,family))return 75;return 30;
    }
    public int formality(List<String> a,List<String> b) {
        if(a.size()!=1 || b.size()!=1 || !FORMALITY.containsKey(a.getFirst()) || !FORMALITY.containsKey(b.getFirst()))return 40;
        return Math.max(0,100-25*Math.abs(FORMALITY.get(a.getFirst())-FORMALITY.get(b.getFirst())));
    }
    public int pattern(List<String> a,List<String> b,MatchMode mode) {
        var known=Set.of("SOLID","STRIPED","CHECKED","FLORAL","POLKA_DOT","ANIMAL","PRINTED","TEXTURED","COLOUR_BLOCK");
        if(a.size()!=1 || b.size()!=1 || !known.contains(a.getFirst()) || !known.contains(b.getFirst()))return 40;
        if(a.equals(b))return mode==MatchMode.EXACT?100:mode==MatchMode.COORDINATED?90:75;
        if(a.contains("SOLID") || b.contains("SOLID"))return mode==MatchMode.EXACT?65:95;
        return mode==MatchMode.EXACT?25:45;
    }
    public int seasons(List<String> a,List<String> b) {var x=known(a,ClothingVocabulary.SEASONS);var y=known(b,ClothingVocabulary.SEASONS);return x.isEmpty() || y.isEmpty()?40:Collections.disjoint(x,y)?25:100;}
    public int category(ProductCategory a,ProductCategory b){return a==ProductCategory.ACCESSORY || b==ProductCategory.ACCESSORY?0:a==ProductCategory.OTHER || b==ProductCategory.OTHER?40:a==b?100:a==ProductCategory.FOOTWEAR || b==ProductCategory.FOOTWEAR?60:85;}
    private Set<String> known(List<String> values,Set<String> allowed){var result=new HashSet<>(values);result.retainAll(allowed);result.remove("UNKNOWN");return result;}
}
