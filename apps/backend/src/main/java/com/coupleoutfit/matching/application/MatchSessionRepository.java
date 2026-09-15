package com.coupleoutfit.matching.application;
import com.coupleoutfit.matching.domain.*;
import com.coupleoutfit.analysis.domain.AnalysisRevision;
import java.time.Instant;
import java.util.*;
public interface MatchSessionRepository {
    MatchSession createOrFind(CreateMatchCommand command,String owner,String requestKey,int candidateLimit,int resultLimit,int minimumScore,Instant now);
    MatchSession findOwned(UUID id,String owner);
    AnalysisRevision revision(UUID sessionId);
    boolean claim(UUID id,Instant now);
    void complete(UUID id,List<MatchResult> results,Instant now);
    void fail(UUID id,String code,Instant now);
    List<MatchResultView> results(UUID id);
}
