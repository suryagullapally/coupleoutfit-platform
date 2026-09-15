package com.coupleoutfit.compatibility.application;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.analysis.application.ClothingAnalysisRepository;
import com.coupleoutfit.analysis.domain.AnalysisStatus;
import com.coupleoutfit.source.application.SourceItemRepository;
import com.coupleoutfit.source.domain.SourceStatus;
import com.coupleoutfit.catalogue.domain.ProductTarget;
import java.util.*;
public final class RetrieveCompatibilityCandidates {
    private final SourceItemRepository sources;private final ClothingAnalysisRepository analyses;private final EnsureAnalysisEmbedding embeddings;private final CandidateRetrievalPort candidates;
    public RetrieveCompatibilityCandidates(SourceItemRepository sources,ClothingAnalysisRepository analyses,EnsureAnalysisEmbedding embeddings,CandidateRetrievalPort candidates) {
        this.sources=sources;this.analyses=analyses;this.embeddings=embeddings;this.candidates=candidates;
    }
    public List<CompatibilityCandidate> retrieve(UUID sourceId,String owner,ProductTarget target,int limit) {
        var source=sources.findOwned(sourceId,owner);
        var snapshot=analyses.findBySource(sourceId).orElseThrow(() -> new EmbeddingException("INVALID_COMPATIBILITY_REQUEST"));
        if(source.item().status()!=SourceStatus.READY_FOR_ANALYSIS || snapshot.analysis().status()!=AnalysisStatus.COMPLETED || snapshot.currentRevision()==null)
            throw new EmbeddingException("INVALID_COMPATIBILITY_REQUEST");
        var revision=snapshot.currentRevision();
        var request=new CompatibilityRequest(revision.id(),source.item().market(),target,revision.attributes().category(),limit);
        var embedding=embeddings.ensure(revision);
        if(!analyses.findBySource(sourceId).orElseThrow().analysis().currentRevisionId().equals(revision.id())) throw new EmbeddingException("INVALID_COMPATIBILITY_REQUEST");
        return candidates.retrieve(request,embedding,revision.attributes());
    }
}
