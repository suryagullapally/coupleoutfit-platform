package com.coupleoutfit.compatibility.domain;
import java.util.*;
public record ProductEmbedding(UUID productId,EmbeddingData data) {
    public ProductEmbedding { Objects.requireNonNull(productId);Objects.requireNonNull(data); }
}
