package com.coupleoutfit.market.application;

import com.coupleoutfit.market.domain.MarketContext;

public final class MarketContextResolver {
    private final MarketRepository repository;
    private final MarketContext configured;

    public MarketContextResolver(MarketRepository repository, String countryCode, String currencyCode, String localeCode) {
        this.repository = java.util.Objects.requireNonNull(repository);
        try { this.configured = new MarketContext(countryCode, currencyCode, localeCode); }
        catch (IllegalArgumentException ex) { throw new MarketConfigurationException(); }
    }

    public MarketContext resolve() {
        var country = repository.findCountry(configured.countryCode()).orElseThrow(MarketConfigurationException::new);
        var currency = repository.findCurrency(configured.currencyCode()).orElseThrow(MarketConfigurationException::new);
        var locale = repository.findLocale(configured.localeCode()).orElseThrow(MarketConfigurationException::new);
        if (!country.active() || !currency.active() || !locale.active()
                || !locale.countryCode().equals(country.code())
                || !locale.defaultCurrencyCode().equals(currency.code())) throw new MarketConfigurationException();
        return configured;
    }
}
