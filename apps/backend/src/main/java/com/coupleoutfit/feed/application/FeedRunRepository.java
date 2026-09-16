package com.coupleoutfit.feed.application;
import com.coupleoutfit.feed.domain.*;
import java.time.Instant;
import java.util.*;
public interface FeedRunRepository {
    FeedRunContext start(UUID feedId,Instant now);
    void processing(UUID runId);
    long applyBatch(FeedRunContext context,List<FeedRowOutcome> rows,Instant now);
    AffiliateFeedRun complete(UUID runId,Instant now);
    AffiliateFeedRun fail(UUID runId,String code,Instant now);
    AffiliateFeedRun find(UUID runId);
}
