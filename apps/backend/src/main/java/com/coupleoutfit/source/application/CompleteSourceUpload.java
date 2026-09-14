package com.coupleoutfit.source.application;

import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import java.time.Clock;
import java.util.UUID;

public final class CompleteSourceUpload {
    private final SourceItemRepository repository;
    private final ImageVerifier verifier;
    private final SourceAssetStorage storage;
    private final Clock clock;
    public CompleteSourceUpload(SourceItemRepository repository, ImageVerifier verifier, SourceAssetStorage storage, Clock clock) {
        this.repository=repository; this.verifier=verifier; this.storage=storage; this.clock=clock;
    }
    public SourceSnapshot complete(UUID id, UUID assetId, String owner) {
        var pending=repository.beginVerification(id,assetId,owner,clock.instant());
        if(pending.asset().status()==AssetStatus.VERIFIED) return pending;
        final SourceAsset verified;
        var claim=pending.item().updatedAt();
        try {
            verified=verifier.verify(pending.asset(),clock.instant());
        } catch(SourceException invalid) {
            if(invalid.code().equals("SOURCE_VERIFICATION_BUSY") || invalid.code().equals("SOURCE_STORAGE_UNAVAILABLE")) {
                repository.releaseVerification(id,owner,claim,clock.instant());
                throw invalid;
            }
            var rejected=repository.finishVerification(id,pending.asset().rejected(),false,claim,clock.instant());
            if(rejected.asset().status()==AssetStatus.VERIFIED) return rejected;
            try { storage.deleteObject(pending.asset().storageKey()); } catch(RuntimeException ignored) { /* Retention cleanup remains a launch prerequisite. */ }
            throw invalid;
        }
        return repository.finishVerification(id,verified,true,claim,clock.instant());
    }
}
