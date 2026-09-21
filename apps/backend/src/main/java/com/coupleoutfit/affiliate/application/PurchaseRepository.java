package com.coupleoutfit.affiliate.application;

import com.coupleoutfit.affiliate.domain.AffiliateDestinationPolicy.Destination;
import java.util.Set;
import java.util.UUID;

public interface PurchaseRepository {
    /** Null means currently non-purchasable. Called only after MatchSession owner authorization. */
    String purchaseUrl(UUID matchResultId);
    /** Returns only after click insertion commits. */
    Destination recordRedirect(String token, String requestId);
    void revoke(UUID purchaseLinkId);
    void approveDestinationHosts(UUID programId, Set<String> hosts);
}
