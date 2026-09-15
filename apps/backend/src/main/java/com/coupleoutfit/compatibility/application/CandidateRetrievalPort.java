package com.coupleoutfit.compatibility.application;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.analysis.domain.ClothingAttributes;
import java.util.List;
public interface CandidateRetrievalPort {
    List<CompatibilityCandidate> retrieve(CompatibilityRequest request,AnalysisEmbedding embedding,ClothingAttributes sourceAttributes);
}
