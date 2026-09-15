package com.coupleoutfit.compatibility.application;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.analysis.domain.AnalysisRevision;
public final class EnsureAnalysisEmbedding {
    private final EnsureEmbedding ensure;private final EmbeddingDescriptorFactory descriptors;
    public EnsureAnalysisEmbedding(EnsureEmbedding ensure,EmbeddingDescriptorFactory descriptors){this.ensure=ensure;this.descriptors=descriptors;}
    /** Internal explicit revision operation; public ownership is enforced by retrieval orchestration. */
    public AnalysisEmbedding ensure(AnalysisRevision revision) {
        return new AnalysisEmbedding(revision.id(),ensure.ensure(new EmbeddingSource(EmbeddingSource.Kind.ANALYSIS,revision.id(),revision.id().toString(),revision.createdAt()),descriptors.analysis(revision)));
    }
}
