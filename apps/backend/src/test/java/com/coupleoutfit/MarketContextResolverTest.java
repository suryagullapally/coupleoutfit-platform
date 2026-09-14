package com.coupleoutfit;

import com.coupleoutfit.market.application.*;
import com.coupleoutfit.market.domain.Country;
import com.coupleoutfit.market.domain.Currency;
import com.coupleoutfit.market.domain.Locale;
import com.coupleoutfit.market.domain.MarketContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MarketContextResolverTest {
    private static class ReferenceData implements MarketRepository {
        Country country = new Country("GB", "United Kingdom", true);
        Currency currency = new Currency("GBP", 2, true);
        Locale locale = new Locale("en-GB", "GB", "GBP", true);
        public Optional<Country> findCountry(String code) { return Optional.ofNullable(country).filter(c -> c.code().equals(code)); }
        public Optional<Currency> findCurrency(String code) { return Optional.ofNullable(currency).filter(c -> c.code().equals(code)); }
        public Optional<Locale> findLocale(String code) { return Optional.ofNullable(locale).filter(c -> c.code().equals(code)); }
    }
    private MarketContextResolver resolver(ReferenceData data) { return new MarketContextResolver(data, "GB", "GBP", "en-GB"); }
    @Test void resolvesApprovedMarket() { assertThat(resolver(new ReferenceData()).resolve()).isEqualTo(new MarketContext("GB", "GBP", "en-GB")); }
    @Test void rejectsMissingCountry() { var data = new ReferenceData(); data.country = null; assertThatThrownBy(() -> resolver(data).resolve()).isInstanceOf(MarketConfigurationException.class); }
    @Test void rejectsInactiveCountry() { var data = new ReferenceData(); data.country = new Country("GB", "United Kingdom", false); assertThatThrownBy(() -> resolver(data).resolve()).isInstanceOf(MarketConfigurationException.class); }
    @Test void rejectsMismatchedLocaleCountry() {
        var data = new ReferenceData(); data.locale = new Locale("en-US", "US", "GBP", true);
        assertThatThrownBy(() -> new MarketContextResolver(data, "GB", "GBP", "en-US").resolve()).isInstanceOf(MarketConfigurationException.class);
    }
    @Test void rejectsMissingCurrency() { var data = new ReferenceData(); data.currency = null; assertThatThrownBy(() -> resolver(data).resolve()).isInstanceOf(MarketConfigurationException.class); }
    @Test void rejectsMissingLocale() { var data = new ReferenceData(); data.locale = null; assertThatThrownBy(() -> resolver(data).resolve()).isInstanceOf(MarketConfigurationException.class); }
    @Test void rejectsInactiveCurrencyAndLocale() {
        var data = new ReferenceData(); data.currency = new Currency("GBP", 2, false);
        assertThatThrownBy(() -> resolver(data).resolve()).isInstanceOf(MarketConfigurationException.class);
        data.currency = new Currency("GBP", 2, true); data.locale = new Locale("en-GB", "GB", "GBP", false);
        assertThatThrownBy(() -> resolver(data).resolve()).isInstanceOf(MarketConfigurationException.class);
    }
    @Test void rejectsCurrencyMismatchAndMalformedConfiguration() {
        var data = new ReferenceData(); data.locale = new Locale("en-GB", "GB", "USD", true);
        assertThatThrownBy(() -> resolver(data).resolve()).isInstanceOf(MarketConfigurationException.class);
        assertThatThrownBy(() -> new MarketContextResolver(data, "", "GBP", "en-GB")).isInstanceOf(MarketConfigurationException.class);
    }
}
