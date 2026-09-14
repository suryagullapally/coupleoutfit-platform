package com.coupleoutfit.source.application;

import com.coupleoutfit.source.domain.*;
import java.time.Instant;
import java.util.UUID;

public interface SourceItemRepository {
    void releaseVerification(UUID id, String owner, Instant claim, Instant now);
    SourceSnapshot create(SourceItem item, SourceAsset asset, SourceUrl url);
    SourceSnapshot findOwned(UUID id, String owner);
    SourceSnapshot startFallback(UUID id, String owner, Instant now);
    SourceSnapshot beginVerification(UUID id, UUID asset, String owner, Instant now);
    SourceSnapshot finishVerification(UUID id, SourceAsset asset, boolean success, Instant claim, Instant now);
}
