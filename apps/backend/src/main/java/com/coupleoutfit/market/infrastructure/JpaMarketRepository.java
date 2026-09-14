package com.coupleoutfit.market.infrastructure;

import com.coupleoutfit.market.application.MarketRepository;
import com.coupleoutfit.market.domain.Country;
import com.coupleoutfit.market.domain.Currency;
import com.coupleoutfit.market.domain.Locale;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaMarketRepository implements MarketRepository {
    private final EntityManager entityManager;
    public JpaMarketRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public Optional<Country> findCountry(String code) { return Optional.ofNullable(entityManager.find(CountryEntity.class, code)).map(CountryEntity::toDomain); }
    public Optional<Currency> findCurrency(String code) { return Optional.ofNullable(entityManager.find(CurrencyEntity.class, code)).map(CurrencyEntity::toDomain); }
    public Optional<Locale> findLocale(String code) { return Optional.ofNullable(entityManager.find(LocaleEntity.class, code)).map(LocaleEntity::toDomain); }
}
