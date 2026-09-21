package com.coupleoutfit.affiliate.domain;

import com.coupleoutfit.market.domain.MarketContext;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** REDIRECTED means a redirect was committed for issuance, not proof of arrival at the retailer. */
public record AffiliateClick(UUID id, UUID purchaseLinkId, UUID matchSessionId, UUID matchResultId,
        UUID productOfferId, UUID retailerMarketId, UUID affiliateProgramId, MarketContext market,
        String requestId, String destinationHost, String destinationUrlHash, Instant redirectedAt) {
    public AffiliateClick {
        Objects.requireNonNull(id); Objects.requireNonNull(purchaseLinkId); Objects.requireNonNull(matchSessionId);
        Objects.requireNonNull(matchResultId); Objects.requireNonNull(productOfferId); Objects.requireNonNull(retailerMarketId);
        Objects.requireNonNull(affiliateProgramId); Objects.requireNonNull(market); Objects.requireNonNull(redirectedAt);
        if(destinationHost==null || destinationHost.isBlank() || destinationUrlHash==null || !destinationUrlHash.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid click destination.");
        if(requestId!=null && !requestId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) throw new IllegalArgumentException("Invalid correlation ID.");
    }
}
