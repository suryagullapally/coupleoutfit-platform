package com.coupleoutfit.compatibility.domain;
public record EmbeddingModelMetadata(String provider,String model,int dimensions) {
    public EmbeddingModelMetadata {
        if(provider==null || !provider.matches("[A-Z][A-Z0-9_]{0,31}") || model==null || !model.matches("[A-Za-z0-9._:-]{1,120}") || dimensions<1 || dimensions>16000)
            throw new IllegalArgumentException("Invalid embedding model metadata.");
    }
}
