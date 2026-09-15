package com.coupleoutfit.analysis.application;

import com.coupleoutfit.analysis.domain.*;
import java.util.Objects;

public record ClothingAnalysisProviderResult(ClothingAttributes attributes,AttributeConfidence confidence,String provider,String model,String providerRequestId,String promptVersion) {
    public ClothingAnalysisProviderResult { Objects.requireNonNull(attributes); Objects.requireNonNull(confidence);
        if(!"OPENAI".equals(provider) || model==null || model.isBlank() || model.length()>120
                || promptVersion==null || promptVersion.isBlank() || promptVersion.length()>100
                || providerRequestId!=null && !providerRequestId.matches("[A-Za-z0-9_-]{1,200}")) throw new AnalysisException("ANALYSIS_INVALID_RESPONSE");
    }
}
