package com.coupleoutfit;

import com.coupleoutfit.market.domain.Country;
import com.coupleoutfit.market.domain.Currency;
import com.coupleoutfit.market.domain.Locale;
import com.coupleoutfit.retailer.domain.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RetailerDomainTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private Retailer retailer(String slug, String name) { return new Retailer(UUID.randomUUID(), slug, name, RetailerStatus.ACTIVE, now, now); }
    private RetailerMarket market(String domain, Country country, Currency currency, Locale locale) {
        return new RetailerMarket(UUID.randomUUID(), retailer("example", "Example"), country, currency, locale, domain, RetailerStatus.ACTIVE, false, false, now, now);
    }
    @Test void normalizesSlugAndRejectsInvalidRetailerData() {
        assertThat(retailer(" Example-Store ", " Example Store ").slug()).isEqualTo("example-store");
        for (String slug : new String[]{"", "bad_slug", "a--b", "-store"}) assertThatThrownBy(() -> retailer(slug, "Example")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retailer("example", " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Retailer(UUID.randomUUID(), "example", "Example", RetailerStatus.ACTIVE, now, now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void validatesMarketRelationshipsAndCanonicalHost() {
        var country = new Country("GB", "United Kingdom", true);
        var currency = new Currency("GBP", 2, true);
        var locale = new Locale("en-GB", "GB", "GBP", true);
        assertThat(market(" SHOP.EXAMPLE.COM ", country, currency, locale).domain()).isEqualTo("shop.example.com");
        for (String host : new String[]{"", "https://shop.example.com", "shop.example.com/path", "shop.example.com:443", "shop.example.com.", "-shop.example.com"}) {
            assertThatThrownBy(() -> market(host, country, currency, locale)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> market("shop.example.com", new Country("US", "United States", true), currency, locale)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> market("shop.example.com", country, new Currency("USD", 2, true), locale)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> market("shop.example.com", null, currency, locale)).isInstanceOf(NullPointerException.class);
    }
}
