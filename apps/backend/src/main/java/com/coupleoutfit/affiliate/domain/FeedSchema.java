package com.coupleoutfit.affiliate.domain;
import java.util.*;
public record FeedSchema(Map<FeedField,String> columns) {
    public static final Set<FeedField> REQUIRED=Set.of(FeedField.RETAILER_PRODUCT_ID,FeedField.TITLE,FeedField.PRODUCT_URL,FeedField.CATEGORY,FeedField.TARGET);
    public FeedSchema {
        columns=Map.copyOf(columns);
        if(!columns.keySet().containsAll(REQUIRED) || columns.size()>FeedField.values().length || new HashSet<>(columns.values()).size()!=columns.size()
                || columns.values().stream().anyMatch(v->!v.matches("[A-Za-z][A-Za-z0-9_ -]{0,79}")))throw new IllegalArgumentException("Invalid feed column mapping.");
        if(columns.containsKey(FeedField.PRICE)!=columns.containsKey(FeedField.CURRENCY))throw new IllegalArgumentException("Price and currency must be mapped together.");
    }
    public static FeedSchema genericCsv() {
        var fields=new EnumMap<FeedField,String>(FeedField.class);
        for(var field:FeedField.values())fields.put(field,field.name().toLowerCase(java.util.Locale.ROOT));
        return new FeedSchema(fields);
    }
}
