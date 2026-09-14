package com.coupleoutfit.source.application;

import java.time.Duration;
import java.util.Set;
public record UploadPolicy(long maxBytes, long maxPixels, int maxDimension, Set<String> allowedMediaTypes, Duration authorizationTtl) {
    public UploadPolicy {
        allowedMediaTypes=Set.copyOf(allowedMediaTypes);
        if(maxBytes<=0 || maxBytes>10485760 || maxPixels<=0 || maxPixels>25000000 || maxDimension<=0 || maxDimension>10000
                || authorizationTtl.isNegative() || authorizationTtl.isZero() || authorizationTtl.compareTo(Duration.ofHours(1))>0
                || allowedMediaTypes.isEmpty() || !Set.of("image/jpeg","image/png","image/webp").containsAll(allowedMediaTypes)) {
            throw new IllegalArgumentException("Invalid upload safety policy.");
        }
    }
}
