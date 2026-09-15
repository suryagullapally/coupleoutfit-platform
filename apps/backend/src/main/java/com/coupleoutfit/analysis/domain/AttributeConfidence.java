package com.coupleoutfit.analysis.domain;

public record AttributeConfidence(Double category,Double primaryColors,Double materialAppearance) {
    public AttributeConfidence { validate(category); validate(primaryColors); validate(materialAppearance); }
    private static void validate(Double value) {
        if(value!=null && (!Double.isFinite(value) || value<0 || value>1)) throw new IllegalArgumentException("Invalid attribute confidence.");
    }
}
