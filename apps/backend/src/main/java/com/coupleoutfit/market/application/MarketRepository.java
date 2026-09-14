package com.coupleoutfit.market.application;

import com.coupleoutfit.market.domain.Country;
import com.coupleoutfit.market.domain.Currency;
import com.coupleoutfit.market.domain.Locale;
import java.util.Optional;

public interface MarketRepository {
    Optional<Country> findCountry(String code);
    Optional<Currency> findCurrency(String code);
    Optional<Locale> findLocale(String code);
}
