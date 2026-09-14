package com.coupleoutfit.catalogue;

import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.market.domain.Country;
import com.coupleoutfit.market.domain.Currency;
import com.coupleoutfit.market.domain.Locale;
import com.coupleoutfit.retailer.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CatalogueDomainTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final UUID retailerId = UUID.randomUUID();
    private final UUID marketId = UUID.randomUUID();
    private Product product() {
        return new Product(UUID.randomUUID(), retailerId, "Oxford shirt", null, ProductCategory.SHIRT,
                "oxford_shirt", ProductTarget.MENSWEAR, ProductAttributes.empty(), "1", ProductStatus.ACTIVE, NOW, NOW);
    }
    private RetailerMarket market(boolean enabled) {
        return new RetailerMarket(marketId, new Retailer(retailerId, "example", "Example", RetailerStatus.ACTIVE, NOW, NOW),
                new Country("GB", "United Kingdom", true), new Currency("GBP", 2, true),
                new Locale("en-GB", "GB", "GBP", true), "example.com", RetailerStatus.ACTIVE, enabled, false, NOW, NOW);
    }
    private ProductOffer offer(UUID productId, String url, Money money) {
        return new ProductOffer(UUID.randomUUID(), productId, marketId, "shirt-1", "Shirt", url,
                "https://example.com/?private=secret", money, AvailabilityStatus.UNKNOWN, null, true, null, null, NOW, NOW);
    }
    @Test void moneyUsesCanonicalCodesAndIntegerMinorUnits() {
        assertThat(new Money(4999, "GBP")).isEqualTo(new Money(4999, "GBP"));
        assertThat(new Money(0, "GBP").amountMinor()).isZero();
        assertThat(new Money(Long.MAX_VALUE, "GBP").amountMinor()).isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(() -> new Money(-1, "GBP")).isInstanceOf(IllegalArgumentException.class);
        for (String code : new String[]{null, "", "gbp", "£", "ZZZ"}) {
            assertThatThrownBy(() -> new Money(1, code)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void productRequiresIdentityControlledClassificationAndVersion() {
        assertThat(product().subcategory()).isEqualTo("OXFORD_SHIRT");
        assertThatThrownBy(() -> new Product(UUID.randomUUID(), retailerId, " ", null, ProductCategory.TOP,
                null, ProductTarget.WOMENSWEAR, ProductAttributes.empty(), "1", ProductStatus.ACTIVE, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductCategory.valueOf("UNCLASSIFIED")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductTarget.valueOf("PERSON")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Product(UUID.randomUUID(), retailerId, "Top", null, null,
                null, ProductTarget.WOMENSWEAR, ProductAttributes.empty(), "1", ProductStatus.ACTIVE, NOW, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Product(UUID.randomUUID(), retailerId, "Top", null, ProductCategory.TOP,
                null, ProductTarget.WOMENSWEAR, ProductAttributes.empty(), "2", ProductStatus.ACTIVE, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void attributesAreBoundedDeeplyImmutableAndDeterministic() {
        var tokens = new ArrayList<>(List.of("WHITE", "NAVY", "NAVY"));
        var input = new HashMap<String, List<String>>(); input.put("primaryColors", tokens);
        var attributes = new ProductAttributes(input);
        tokens.clear(); input.clear();
        assertThat(attributes.values().get("primaryColors")).containsExactly("NAVY", "WHITE");
        assertThatThrownBy(() -> attributes.values().put("fit", List.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> attributes.values().get("primaryColors").clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ProductAttributes(Map.of("email", List.of("X")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductAttributes(Map.of("fit", List.of("arbitrary text")))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void urlsAreValidatedWithoutLeakingAffiliateValues() {
        for (String url : List.of("https://example.com/a?b=c", "http://example.com/a")) {
            assertThat(offer(product().id(), url, null).price()).isNull();
        }
        for (String url : List.of("javascript:alert(1)", "file:///tmp/a", "data:text/plain,x", "ftp://example.com/a",
                "https:///missing-host", "https://user:secret@example.com", "https://example.com:70000/a")) {
            assertThatThrownBy(() -> offer(product().id(), url, null)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("A valid HTTP(S) catalogue URL is required.");
        }
        assertThat(offer(product().id(), "https://example.com", null).toString()).doesNotContain("secret", "private", "https");
    }
    @Test void offerChecksOwnershipCurrencyAndEnabledMarket() {
        var product = product();
        offer(product.id(), "https://example.com", new Money(4999, "GBP")).validateFor(product, market(true));
        assertThatThrownBy(() -> offer(product.id(), "https://example.com", new Money(1, "USD")).validateFor(product, market(true))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> offer(product.id(), "https://example.com", null).validateFor(product, market(false))).isInstanceOf(IllegalArgumentException.class);
        var other = new Product(product.id(), UUID.randomUUID(), "Shirt", null, ProductCategory.SHIRT, null,
                ProductTarget.MENSWEAR, ProductAttributes.empty(), "1", ProductStatus.ACTIVE, NOW, NOW);
        assertThatThrownBy(() -> offer(product.id(), "https://example.com", null).validateFor(other, market(true))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void variantAllowsUnknownIdentityButRejectsBlankValuesAndInvalidTime() {
        new ProductVariant(UUID.randomUUID(), UUID.randomUUID(), null, null, null, null,
                AvailabilityStatus.UNKNOWN, ProductAttributes.empty(), null, NOW, NOW);
        assertThatThrownBy(() -> new ProductVariant(UUID.randomUUID(), UUID.randomUUID(), " ", null, null, null,
                AvailabilityStatus.UNKNOWN, ProductAttributes.empty(), null, NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductVariant(UUID.randomUUID(), UUID.randomUUID(), "id", null, null, null,
                AvailabilityStatus.UNKNOWN, ProductAttributes.empty(), null, NOW, NOW.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }
}
