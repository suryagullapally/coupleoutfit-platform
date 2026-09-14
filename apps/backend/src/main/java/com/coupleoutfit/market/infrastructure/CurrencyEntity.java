package com.coupleoutfit.market.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity(name = "CurrencyRecord")
@Table(name = "currency")
public class CurrencyEntity {
    @Id @Column(length = 3) private String code;
    @Column(nullable = false) private short minorUnitDigits;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected CurrencyEntity() {}
    public com.coupleoutfit.market.domain.Currency toDomain() { return new com.coupleoutfit.market.domain.Currency(code, minorUnitDigits, active); }
}
