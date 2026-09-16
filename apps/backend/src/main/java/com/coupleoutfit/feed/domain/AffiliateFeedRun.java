package com.coupleoutfit.feed.domain;
import java.time.Instant;
import java.util.*;
public record AffiliateFeedRun(UUID id,UUID affiliateFeedId,FeedRunStatus status,FeedCounters counters,Map<String,Long> rejectionCounts,
        String failureCode,Instant startedAt,Instant completedAt,Instant createdAt) {
    public AffiliateFeedRun {
        Objects.requireNonNull(id);Objects.requireNonNull(affiliateFeedId);Objects.requireNonNull(status);Objects.requireNonNull(counters);
        Objects.requireNonNull(startedAt);Objects.requireNonNull(createdAt);rejectionCounts=Map.copyOf(rejectionCounts);
        if(startedAt.isBefore(createdAt) || completedAt!=null && completedAt.isBefore(startedAt))throw new IllegalArgumentException("Invalid run timestamps.");
        boolean terminal=status==FeedRunStatus.COMPLETED || status==FeedRunStatus.COMPLETED_WITH_ERRORS || status==FeedRunStatus.FAILED;
        if(terminal!=(completedAt!=null) || (status==FeedRunStatus.FAILED)!=(failureCode!=null))throw new IllegalArgumentException("Invalid run state.");
        if(status==FeedRunStatus.COMPLETED && counters.itemsRejected()!=0 || status==FeedRunStatus.COMPLETED_WITH_ERRORS && counters.itemsRejected()==0)throw new IllegalArgumentException("Invalid completion outcome.");
        if(rejectionCounts.size()>32 || rejectionCounts.entrySet().stream().anyMatch(e->!e.getKey().matches("FEED_[A-Z_]{1,80}") || e.getValue()<1))throw new IllegalArgumentException("Invalid rejection counts.");
    }
    public void requireTransition(FeedRunStatus next) {
        if(!(status==FeedRunStatus.STARTED && (next==FeedRunStatus.PROCESSING || next==FeedRunStatus.FAILED)
                || status==FeedRunStatus.PROCESSING && (next==FeedRunStatus.COMPLETED || next==FeedRunStatus.COMPLETED_WITH_ERRORS || next==FeedRunStatus.FAILED)))throw new IllegalStateException("Invalid feed run transition.");
    }
}
