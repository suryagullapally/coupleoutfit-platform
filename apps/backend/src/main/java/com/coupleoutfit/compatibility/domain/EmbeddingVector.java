package com.coupleoutfit.compatibility.domain;
import java.util.*;
public record EmbeddingVector(List<Float> values) {
    public EmbeddingVector {
        values=List.copyOf(values);
        if(values.isEmpty() || values.size()>16000 || values.stream().anyMatch(v -> !Float.isFinite(v)) || values.stream().allMatch(v -> v==0f))
            throw new IllegalArgumentException("Invalid embedding vector.");
    }
    @Override public String toString() { return "EmbeddingVector[dimensions="+values.size()+"]"; }
}
