package com.coupleoutfit.analysis.application;

import com.coupleoutfit.source.application.SourceItemRepository;
import com.coupleoutfit.analysis.domain.ClothingAttributes;
import java.time.Clock;
import java.util.UUID;

public final class ReviewClothingAnalysis {
    private final SourceItemRepository sources;
    private final ClothingAnalysisRepository repository;
    private final Clock clock;
    public ReviewClothingAnalysis(SourceItemRepository sources,ClothingAnalysisRepository repository,Clock clock) {
        this.sources=sources;this.repository=repository;this.clock=clock;
    }
    public AnalysisSnapshot get(UUID source,String owner) {
        sources.findOwned(source,owner);
        return repository.findBySource(source).orElseThrow(() -> new AnalysisException("ANALYSIS_NOT_FOUND"));
    }
    public AnalysisSnapshot correct(UUID source,String owner,UUID parent,ClothingAttributes attributes) {
        sources.findOwned(source,owner);
        if(parent==null || attributes==null) throw new AnalysisException("ANALYSIS_INVALID_ATTRIBUTES");
        return repository.correct(source,parent,attributes,clock.instant());
    }
}
