package com.coupleoutfit.catalogue.domain;

import java.util.*;

/** Version 1: bounded named token lists; no arbitrary JSON objects or personal data. */
public record ProductAttributes(Map<String, List<String>> values) {
    private static final Set<String> KEYS = Set.of("primaryColors", "secondaryColors", "pattern",
            "materialAppearance", "fit", "formality", "seasonality", "styleTags");
    public ProductAttributes {
        Objects.requireNonNull(values, "Attributes are required.");
        var copy = new TreeMap<String, List<String>>();
        values.forEach((key, tokens) -> {
            if (!KEYS.contains(key) || tokens == null || tokens.size() > 20) {
                throw new IllegalArgumentException("Invalid attribute key or list.");
            }
            var normalized = new TreeSet<String>();
            for (String token : tokens) {
                if (token == null || !token.matches("[A-Z][A-Z0-9_]{0,63}")) {
                    throw new IllegalArgumentException("Attribute values must be bounded uppercase tokens.");
                }
                normalized.add(token);
            }
            copy.put(key, List.copyOf(normalized));
        });
        values = Collections.unmodifiableMap(copy);
    }
    public static ProductAttributes empty() { return new ProductAttributes(Map.of()); }
}
