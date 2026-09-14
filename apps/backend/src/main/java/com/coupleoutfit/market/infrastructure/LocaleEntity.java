package com.coupleoutfit.market.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity(name = "LocaleRecord")
@Table(name = "locale")
public class LocaleEntity {
    @Id @Column(length = 64) private String code;
    @Column(nullable = false, length = 2) private String countryCode;
    @Column(nullable = false, length = 3) private String defaultCurrencyCode;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected LocaleEntity() {}
    public com.coupleoutfit.market.domain.Locale toDomain() { return new com.coupleoutfit.market.domain.Locale(code, countryCode, defaultCurrencyCode, active); }
}
