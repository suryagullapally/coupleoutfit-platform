package com.coupleoutfit.compatibility.infrastructure;
import com.coupleoutfit.compatibility.domain.EmbeddingVector;
import java.util.*;
final class VectorStorage {
    static final int DIMENSIONS=1536;
    static String encode(EmbeddingVector vector) {
        if(vector.values().size()!=DIMENSIONS)throw new com.coupleoutfit.compatibility.application.EmbeddingException("EMBEDDING_DIMENSION_MISMATCH");
        return vector.values().toString();
    }
    static EmbeddingVector decode(String value) {
        return new EmbeddingVector(Arrays.stream(value.substring(1,value.length()-1).split(",")).map(Float::valueOf).toList());
    }
}
