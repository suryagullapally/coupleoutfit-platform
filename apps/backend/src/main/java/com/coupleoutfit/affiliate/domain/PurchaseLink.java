package com.coupleoutfit.affiliate.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PurchaseLink(UUID id, String tokenHash, String tokenNonce, UUID matchResultId,
        UUID matchSessionId, UUID productOfferId, PurchaseLinkStatus status, Instant expiresAt, Instant createdAt) {
    public PurchaseLink {
        Objects.requireNonNull(id); Objects.requireNonNull(matchResultId); Objects.requireNonNull(matchSessionId);
        Objects.requireNonNull(productOfferId); Objects.requireNonNull(status); Objects.requireNonNull(createdAt);
        if(tokenHash==null || !tokenHash.matches("[a-f0-9]{64}") || tokenNonce==null || !tokenNonce.matches("[a-f0-9]{64}")
                || expiresAt==null || !expiresAt.isAfter(createdAt)) throw new IllegalArgumentException("Invalid purchase link.");
    }
    public void requireActive(Instant now) {
        if(status==PurchaseLinkStatus.REVOKED) throw new PurchaseException("PURCHASE_LINK_REVOKED");
        if(!expiresAt.isAfter(now)) throw new PurchaseException("PURCHASE_LINK_EXPIRED");
    }
    @Override public String toString() { return "PurchaseLink[id="+id+", status="+status+"]"; }
}
