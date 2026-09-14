package com.coupleoutfit;

import com.coupleoutfit.market.domain.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MarketDomainTest {
    @Test void contextIsValidatedAndValueBased() {
        assertThat(new MarketContext("GB", "GBP", "en-GB")).isEqualTo(new MarketContext("GB", "GBP", "en-GB"));
        assertThatThrownBy(() -> new MarketContext("", "GBP", "en-GB")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketContext("GB", " ", "en-GB")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketContext("GB", "GBP", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketContext("gb", "GBP", "en-GB")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketContext("GB", "ZZZ", "en-GB")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void referenceDataRejectsInvalidValues() {
        assertThatThrownBy(() -> new Country("GB", " ", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Currency("GBP", -1, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Locale("en_US", "GB", "GBP", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Locale("en-US", "GB", "GBP", true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new MarketContext("IN", "INR", "en-IN")).isNotNull();
    }
}
