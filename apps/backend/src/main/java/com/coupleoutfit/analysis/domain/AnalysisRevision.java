package com.coupleoutfit.analysis.domain;

import java.time.Instant;
import java.util.*;

public record AnalysisRevision(UUID id,UUID clothingAnalysisId,int revisionNumber,RevisionType revisionType,UUID parentRevisionId,
        ClothingAttributes attributes,AttributeConfidence confidence,String schemaVersion,String provider,String model,String providerRequestId,String promptVersion,Instant createdAt) {
    public AnalysisRevision {
        Objects.requireNonNull(id);Objects.requireNonNull(clothingAnalysisId);Objects.requireNonNull(revisionType);Objects.requireNonNull(attributes);Objects.requireNonNull(createdAt);
        if(!"1".equals(schemaVersion)) throw new IllegalArgumentException("Unsupported analysis schema.");
        if(revisionType==RevisionType.AI_GENERATED) {
            if(revisionNumber!=1 || parentRevisionId!=null || !"OPENAI".equals(provider) || model==null || model.isBlank() || model.length()>120
                    || promptVersion==null || promptVersion.isBlank() || promptVersion.length()>100) throw new IllegalArgumentException("Invalid AI revision metadata.");
        } else if(revisionNumber<2 || parentRevisionId==null || provider!=null || model!=null || providerRequestId!=null || promptVersion!=null || confidence!=null) {
            throw new IllegalArgumentException("Invalid correction revision metadata.");
        }
        if(providerRequestId!=null && !providerRequestId.matches("[A-Za-z0-9_-]{1,200}")) throw new IllegalArgumentException("Invalid provider request reference.");
    }
}
