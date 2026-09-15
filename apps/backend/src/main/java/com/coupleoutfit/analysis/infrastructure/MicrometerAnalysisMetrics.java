package com.coupleoutfit.analysis.infrastructure;

import com.coupleoutfit.analysis.application.AnalysisMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Component
public final class MicrometerAnalysisMetrics implements AnalysisMetrics {
    private final MeterRegistry registry;
    public MicrometerAnalysisMetrics(MeterRegistry registry) { this.registry=registry; }
    @Override public void started() { registry.counter("analysis.request.count").increment(); }
    @Override public void finished(Duration duration,String failureCode) {
        registry.timer("analysis.duration").record(duration);
        if(failureCode==null) registry.counter("analysis.success.count").increment();
        else registry.counter("analysis.failure.count","failureType",failureCode).increment();
    }
    @Override public void retried() { registry.counter("analysis.retry.count").increment(); }
}
