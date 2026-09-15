package com.coupleoutfit.compatibility.application;
import java.time.Instant;
import java.util.*;
public record EmbeddingSource(Kind kind,UUID id,String sourceVersion,Instant sourceUpdatedAt) {
    public enum Kind { PRODUCT, ANALYSIS }
    public EmbeddingSource {Objects.requireNonNull(kind);Objects.requireNonNull(id);Objects.requireNonNull(sourceUpdatedAt);
        if(sourceVersion==null || sourceVersion.isBlank() || sourceVersion.length()>160) throw new IllegalArgumentException("Invalid source version.");}
}
