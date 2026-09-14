package com.coupleoutfit.source.domain;

import java.time.Instant;
import java.util.*;

public record SourceUrl(UUID sourceItemId, String originalUrl, String normalizedUrl, UrlResolutionStatus resolutionStatus,
        String resolverType, UUID resolvedRetailerMarketId, UUID resolvedProductOfferId, String resolvedImageUrl,
        String failureCode, Instant resolvedAt) {
    public SourceUrl {
        Objects.requireNonNull(sourceItemId); Objects.requireNonNull(resolutionStatus); Objects.requireNonNull(resolvedAt);
        if (!SourceUrls.normalize(originalUrl).equals(normalizedUrl)) throw new IllegalArgumentException("Inconsistent source URL.");
        if (!"CATALOGUE".equals(resolverType)) throw new IllegalArgumentException("Invalid resolver type.");
        if (resolutionStatus == UrlResolutionStatus.UNSUPPORTED && (resolvedProductOfferId != null || resolvedImageUrl != null || failureCode == null)) throw new IllegalArgumentException("Invalid unsupported URL result.");
        if (resolutionStatus == UrlResolutionStatus.RESOLVED_CATALOGUE_OFFER) {
            Objects.requireNonNull(resolvedRetailerMarketId); Objects.requireNonNull(resolvedProductOfferId);
            SourceUrls.normalize(resolvedImageUrl);
            if (failureCode != null) throw new IllegalArgumentException("Resolved source cannot have failure reason.");
        }
    }
    @Override public String toString() { return "SourceUrl[sourceItemId="+sourceItemId+", resolutionStatus="+resolutionStatus+"]"; }
}
