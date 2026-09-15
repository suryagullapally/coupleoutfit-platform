package com.coupleoutfit.analysis.domain;

import com.coupleoutfit.catalogue.domain.ProductCategory;
import java.util.*;

public record ClothingAttributes(ProductCategory category,String subcategory,List<String> primaryColors,List<String> secondaryColors,
        Pattern pattern,List<String> materialAppearance,Fit fit,Formality formality,List<String> seasonality,List<String> styleTags,List<String> details) {
    public ClothingAttributes {
        Objects.requireNonNull(category); Objects.requireNonNull(pattern); Objects.requireNonNull(fit); Objects.requireNonNull(formality);
        if(subcategory!=null && !ClothingVocabulary.SUBCATEGORIES.contains(subcategory)) throw new IllegalArgumentException("Invalid subcategory.");
        primaryColors=ClothingVocabulary.tokens(primaryColors,ClothingVocabulary.COLORS,3);
        secondaryColors=ClothingVocabulary.tokens(secondaryColors,ClothingVocabulary.COLORS,3);
        if(!Collections.disjoint(primaryColors,secondaryColors)) throw new IllegalArgumentException("Color lists must be distinct.");
        materialAppearance=ClothingVocabulary.tokens(materialAppearance,ClothingVocabulary.MATERIALS,4);
        seasonality=ClothingVocabulary.tokens(seasonality,ClothingVocabulary.SEASONS,4);
        styleTags=ClothingVocabulary.tokens(styleTags,ClothingVocabulary.STYLES,8);
        details=ClothingVocabulary.tokens(details,ClothingVocabulary.DETAILS,10);
    }
}
