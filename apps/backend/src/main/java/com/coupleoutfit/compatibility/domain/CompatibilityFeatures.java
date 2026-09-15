package com.coupleoutfit.compatibility.domain;
import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.analysis.domain.ClothingAttributes;
import java.util.*;
public record CompatibilityFeatures(double semanticSimilarity,ProductCategory sourceCategory,ProductCategory candidateCategory,
        List<String> sourceColors,List<String> candidateColors,List<String> sourcePattern,List<String> candidatePattern,
        List<String> sourceFormality,List<String> candidateFormality,List<String> sourceStyleTags,List<String> candidateStyleTags,
        List<String> sourceSeasonality,List<String> candidateSeasonality) {
    public CompatibilityFeatures {
        if(!Double.isFinite(semanticSimilarity) || semanticSimilarity<0 || semanticSimilarity>1) throw new IllegalArgumentException("Invalid similarity.");
        Objects.requireNonNull(sourceCategory);Objects.requireNonNull(candidateCategory);
        sourceColors=List.copyOf(sourceColors);candidateColors=List.copyOf(candidateColors);sourcePattern=List.copyOf(sourcePattern);candidatePattern=List.copyOf(candidatePattern);
        sourceFormality=List.copyOf(sourceFormality);candidateFormality=List.copyOf(candidateFormality);sourceStyleTags=List.copyOf(sourceStyleTags);candidateStyleTags=List.copyOf(candidateStyleTags);
        sourceSeasonality=List.copyOf(sourceSeasonality);candidateSeasonality=List.copyOf(candidateSeasonality);
    }
    public static CompatibilityFeatures from(double similarity,ClothingAttributes source,ProductCategory category,ProductAttributes candidate) {
        var c=candidate.values();var colors=new TreeSet<>(source.primaryColors());colors.addAll(source.secondaryColors());
        var otherColors=new TreeSet<>(c.getOrDefault("primaryColors",List.of()));otherColors.addAll(c.getOrDefault("secondaryColors",List.of()));
        return new CompatibilityFeatures(similarity,source.category(),category,List.copyOf(colors),List.copyOf(otherColors),List.of(source.pattern().name()),c.getOrDefault("pattern",List.of()),
                List.of(source.formality().name()),c.getOrDefault("formality",List.of()),source.styleTags(),c.getOrDefault("styleTags",List.of()),source.seasonality(),c.getOrDefault("seasonality",List.of()));
    }
}
