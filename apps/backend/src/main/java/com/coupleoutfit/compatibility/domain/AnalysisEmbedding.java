package com.coupleoutfit.compatibility.domain;
import java.util.*;
public record AnalysisEmbedding(UUID analysisRevisionId,EmbeddingData data) {
    public AnalysisEmbedding { Objects.requireNonNull(analysisRevisionId);Objects.requireNonNull(data); }
}
