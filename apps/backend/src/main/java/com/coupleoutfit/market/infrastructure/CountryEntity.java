package com.coupleoutfit.market.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;

@Entity(name = "CountryRecord")
@Table(name = "country")
public class CountryEntity {
    @Id @Column(length = 2) private String code;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected CountryEntity() {}
    public com.coupleoutfit.market.domain.Country toDomain() { return new com.coupleoutfit.market.domain.Country(code, name, active); }
}
