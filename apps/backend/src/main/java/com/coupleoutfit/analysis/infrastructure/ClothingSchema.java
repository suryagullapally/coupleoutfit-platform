package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.domain.*;
import com.coupleoutfit.analysis.application.AnalysisException;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

public final class ClothingSchema {
    public record Parsed(ClothingAttributes attributes,AttributeConfidence confidence) {}
    private final JsonMapper json=JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    public Parsed parse(String text) {
        try {
            if(text==null || text.length()>32768) throw new IllegalArgumentException();
            var root=json.readTree(text);
            keys(root,Set.of("attributes","confidence"));
            var a=root.get("attributes");
            keys(a,Set.of("category","subcategory","primaryColors","secondaryColors","pattern","materialAppearance","fit","formality","seasonality","styleTags","details"));
            for(String key:List.of("category","pattern","fit","formality")) if(!a.get(key).isString()) throw new IllegalArgumentException();
            if(!a.get("subcategory").isNull() && !a.get("subcategory").isString()) throw new IllegalArgumentException();
            for(String key:List.of("primaryColors","secondaryColors","materialAppearance","seasonality","styleTags","details")) {
                if(!a.get(key).isArray()) throw new IllegalArgumentException();
                for(var value:a.get(key)) if(!value.isString()) throw new IllegalArgumentException();
            }
            var confidence=root.get("confidence");keys(confidence,Set.of("category","primaryColors","materialAppearance"));
            for(var entry:confidence.properties()) if(!entry.getValue().isNull() && !entry.getValue().isNumber()) throw new IllegalArgumentException();
            return new Parsed(json.treeToValue(a,ClothingAttributes.class),json.treeToValue(confidence,AttributeConfidence.class));
        } catch(Exception invalid) { throw new AnalysisException("ANALYSIS_INVALID_RESPONSE"); }
    }
    private static void keys(JsonNode node,Set<String> keys) {
        if(node==null || !node.isObject() || !node.propertyNames().equals(keys)) throw new IllegalArgumentException();
    }
}
