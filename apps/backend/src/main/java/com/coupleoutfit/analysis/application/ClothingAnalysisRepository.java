package com.coupleoutfit.analysis.application;

import com.coupleoutfit.analysis.domain.ClothingAttributes;
import java.time.Instant;
import java.util.*;

public interface ClothingAnalysisRepository {
    record StartClaim(AnalysisSnapshot snapshot,boolean execute) {}
    StartClaim claim(UUID sourceId,Instant now);
    Optional<AnalysisSnapshot> findBySource(UUID sourceId);
    AnalysisSnapshot complete(UUID analysisId,ClothingAnalysisProviderResult result,Instant now);
    void fail(UUID analysisId,String code,Instant now);
    AnalysisSnapshot correct(UUID sourceId,UUID parentId,ClothingAttributes attributes,Instant now);
}
