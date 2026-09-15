package com.coupleoutfit.analysis.application;

import com.coupleoutfit.analysis.domain.*;
public record AnalysisSnapshot(ClothingAnalysis analysis,AnalysisRevision currentRevision) {}
