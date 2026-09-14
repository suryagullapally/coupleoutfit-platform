package com.coupleoutfit.source.infrastructure;

import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity(name="SourceItemRecord")
@Table(name="source_item")
class SourceItemEntity {
    @Id UUID id;
    @Column(length=64) String ownerSessionId;
    @Enumerated(EnumType.STRING) SourceType sourceType;
    @Enumerated(EnumType.STRING) SourceStatus status;
    @Column(length=2) String countryCode;
    @Column(length=3) String currencyCode;
    @Column(length=64) String localeCode;
    Instant createdAt;
    Instant updatedAt;
    protected SourceItemEntity() {}
    SourceItemEntity(SourceItem value) { update(value); }
    void update(SourceItem value) {
        id=value.id();
        ownerSessionId=value.ownerSessionId();
        sourceType=value.sourceType();
        status=value.status();
        countryCode=value.market().countryCode();
        currencyCode=value.market().currencyCode();
        localeCode=value.market().localeCode();
        createdAt=value.createdAt();
        updatedAt=value.updatedAt();
    }
    SourceItem toDomain() { return new SourceItem(id,ownerSessionId,sourceType,status,new MarketContext(countryCode,currencyCode,localeCode),createdAt,updatedAt); }
}
