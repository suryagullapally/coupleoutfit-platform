package com.coupleoutfit.source.domain;

import com.coupleoutfit.market.domain.MarketContext;
import java.time.Instant;
import java.util.*;

public record SourceItem(UUID id, String ownerSessionId, SourceType sourceType, SourceStatus status,
        MarketContext market, Instant createdAt, Instant updatedAt) {
    public SourceItem {
        Objects.requireNonNull(id); Objects.requireNonNull(sourceType); Objects.requireNonNull(status);
        Objects.requireNonNull(market); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        if (ownerSessionId == null || !ownerSessionId.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid owner reference.");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("Invalid source timestamps.");
        if (sourceType == SourceType.IMAGE_UPLOAD && (status == SourceStatus.RESOLVING_URL || status == SourceStatus.IMAGE_UPLOAD_REQUIRED)) {
            throw new IllegalArgumentException("Invalid image source state.");
        }
    }
    public SourceItem transition(SourceStatus next, Instant now) {
        boolean allowed = switch (status) {
            case AWAITING_UPLOAD -> next == SourceStatus.UPLOAD_VERIFYING;
            case UPLOAD_VERIFYING -> next == SourceStatus.AWAITING_UPLOAD || next == SourceStatus.READY_FOR_ANALYSIS || next == SourceStatus.FAILED;
            case RESOLVING_URL -> next == SourceStatus.READY_FOR_ANALYSIS || next == SourceStatus.IMAGE_UPLOAD_REQUIRED || next == SourceStatus.FAILED;
            case IMAGE_UPLOAD_REQUIRED -> next == SourceStatus.AWAITING_UPLOAD;
            default -> false;
        };
        if (!allowed) throw new IllegalStateException("Invalid source transition.");
        return new SourceItem(id, ownerSessionId, sourceType, next, market, createdAt, now);
    }
    @Override public String toString() { return "SourceItem[id="+id+", sourceType="+sourceType+", status="+status+"]"; }
}
