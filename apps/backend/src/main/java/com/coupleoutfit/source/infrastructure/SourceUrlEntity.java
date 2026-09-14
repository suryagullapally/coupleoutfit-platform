package com.coupleoutfit.source.infrastructure;

import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name="SourceUrlRecord")
@Table(name="source_url")
class SourceUrlEntity {
    @Id UUID sourceItemId;
    @Column(length=4096) String originalUrl;
    @Column(length=4096) String normalizedUrl;
    @Enumerated(EnumType.STRING) UrlResolutionStatus resolutionStatus;
    String resolverType;
    UUID resolvedRetailerMarketId;
    UUID resolvedProductOfferId;
    @Column(length=4096) String resolvedImageUrl;
    String failureCode;
    Instant resolvedAt;
    protected SourceUrlEntity() {}
    SourceUrlEntity(SourceUrl value) { update(value); }
    void update(SourceUrl value) {
        sourceItemId=value.sourceItemId();
        originalUrl=value.originalUrl();
        normalizedUrl=value.normalizedUrl();
        resolutionStatus=value.resolutionStatus();
        resolverType=value.resolverType();
        resolvedRetailerMarketId=value.resolvedRetailerMarketId();
        resolvedProductOfferId=value.resolvedProductOfferId();
        resolvedImageUrl=value.resolvedImageUrl();
        failureCode=value.failureCode();
        resolvedAt=value.resolvedAt();
    }
    SourceUrl toDomain() { return new SourceUrl(sourceItemId,originalUrl,normalizedUrl,resolutionStatus,resolverType,resolvedRetailerMarketId,resolvedProductOfferId,resolvedImageUrl,failureCode,resolvedAt); }
}
