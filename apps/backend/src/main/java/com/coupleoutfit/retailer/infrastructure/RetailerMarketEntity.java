package com.coupleoutfit.retailer.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.coupleoutfit.market.infrastructure.CountryEntity;
import com.coupleoutfit.market.infrastructure.CurrencyEntity;
import com.coupleoutfit.market.infrastructure.LocaleEntity;
import com.coupleoutfit.retailer.domain.RetailerMarket;
import com.coupleoutfit.retailer.domain.RetailerStatus;

@Entity(name = "RetailerMarketRecord")
@Table(name = "retailer_market")
public class RetailerMarketEntity {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "retailer_id") private RetailerEntity retailer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "country_code") private CountryEntity country;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "default_currency_code") private CurrencyEntity defaultCurrency;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "locale_code") private LocaleEntity locale;
    @Column(nullable = false, length = 253) private String domain;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private RetailerStatus status;
    @Column(nullable = false) private boolean catalogueEnabled;
    @Column(nullable = false) private boolean matchingEnabled;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected RetailerMarketEntity() {}
    public RetailerMarket toDomain() {
        return new RetailerMarket(id, retailer.toDomain(), country.toDomain(), defaultCurrency.toDomain(), locale.toDomain(), domain, status, catalogueEnabled, matchingEnabled, createdAt, updatedAt);
    }
}
