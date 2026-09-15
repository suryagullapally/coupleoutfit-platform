package com.coupleoutfit.analysis.application;

import java.time.Duration;

public interface AnalysisMetrics {
    void started();
    void finished(Duration duration,String failureCode);
    void retried();
}
