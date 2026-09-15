package com.coupleoutfit.analysis.application;

import com.coupleoutfit.source.application.SourceItemRepository;
import com.coupleoutfit.source.domain.SourceStatus;
import java.time.*;
import java.util.UUID;

public final class StartClothingAnalysis {
    private final SourceItemRepository sources;
    private final ClothingAnalysisRepository repository;
    private final SourceImageAccessPort images;
    private final ClothingAnalysisPort provider;
    private final Clock clock;
    private final AnalysisMetrics metrics;
    private final int maxAttempts;
    private final Duration backoff;
    public StartClothingAnalysis(SourceItemRepository sources,ClothingAnalysisRepository repository,SourceImageAccessPort images,
            ClothingAnalysisPort provider,Clock clock,int maxAttempts,Duration backoff,AnalysisMetrics metrics) {
        this.metrics=metrics;this.sources=sources;this.repository=repository;this.images=images;this.provider=provider;this.clock=clock;this.maxAttempts=maxAttempts;this.backoff=backoff;
        if(maxAttempts<1 || maxAttempts>3 || backoff.isNegative() || backoff.compareTo(Duration.ofSeconds(2))>0) throw new IllegalArgumentException("Invalid analysis retry limits.");
    }
    public AnalysisSnapshot start(UUID sourceId,String owner) {
        var source=sources.findOwned(sourceId,owner);
        if(source.item().status()!=SourceStatus.READY_FOR_ANALYSIS) throw new AnalysisException("ANALYSIS_SOURCE_NOT_READY");
        var claim=repository.claim(sourceId,clock.instant());
        if(!claim.execute()) return claim.snapshot();
        var id=claim.snapshot().analysis().id();
        metrics.started();long started=System.nanoTime();String failure=null;
        try {
            var result=analyse(images.imageFor(source));
            return repository.complete(id,result,clock.instant());
        } catch(AnalysisException error) {
            failure=error.code();repository.fail(id,failure,clock.instant());throw error;
        } catch(RuntimeException error) {
            failure="ANALYSIS_FAILED";repository.fail(id,failure,clock.instant());throw new AnalysisException(failure);
        } finally { metrics.finished(Duration.ofNanos(System.nanoTime()-started),failure); }
    }
    private ClothingAnalysisProviderResult analyse(AnalysisImage image) {
        for(int attempt=1;;attempt++) {
            try { return provider.analyse(image); }
            catch(AnalysisException error) {
                if(!error.transientFailure() || attempt>=maxAttempts) throw error;
                metrics.retried();
                try { Thread.sleep(backoff.multipliedBy(attempt)); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw new AnalysisException("ANALYSIS_PROVIDER_UNAVAILABLE"); }
            }
        }
    }
}
