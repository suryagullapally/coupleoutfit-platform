package com.coupleoutfit.feed;
import com.coupleoutfit.affiliate.domain.FeedField;
import com.coupleoutfit.feed.domain.*;
import com.coupleoutfit.catalogue.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class FeedNormalizerTest {
    private final FeedItemNormalizer normalizer=new FeedItemNormalizer();
    @Test void exactMinorUnitsMissingPriceAndMarketCurrency() {
        var market=FeedFixtures.market();assertThat(normalizer.normalize(FeedFixtures.row(Map.of()),market).price()).isEqualTo(new Money(4999,"GBP"));
        assertThat(normalizer.normalize(FeedFixtures.row(Map.of(FeedField.PRICE,"")),market).price()).isNull();
        assertThatThrownBy(()->normalizer.normalize(FeedFixtures.row(Map.of(FeedField.CURRENCY,"INR")),market)).hasMessage("FEED_CURRENCY_MISMATCH");
        assertThatThrownBy(()->normalizer.normalize(FeedFixtures.row(Map.of(FeedField.CURRENCY,"")),market)).hasMessage("FEED_CURRENCY_MISMATCH");
        var zero=new FeedMarket(market.retailerMarketId(),market.retailerId(),market.context(),0,market.domain());
        assertThat(normalizer.normalize(FeedFixtures.row(Map.of(FeedField.PRICE,"49")),zero).price().amountMinor()).isEqualTo(49);
        for(String invalid:List.of("49.999","-1","NaN","£49.99","1e3","999999999999999999999999999"))
            assertThatThrownBy(()->normalizer.normalize(FeedFixtures.row(Map.of(FeedField.PRICE,invalid)),market)).hasMessage("FEED_PRICE_INVALID");
    }
    @ParameterizedTest @CsvSource({"in stock,IN_STOCK","AVAILABLE,IN_STOCK","out of stock,OUT_OF_STOCK","sold out,OUT_OF_STOCK","unspecified,UNKNOWN"})
    void availability(String value,AvailabilityStatus expected){assertThat(normalizer.normalize(FeedFixtures.row(Map.of(FeedField.AVAILABILITY,value)),FeedFixtures.market()).availability()).isEqualTo(expected);}
    @Test void reliableCategoryTargetAndAttributesOnly() {
        var result=normalizer.normalize(FeedFixtures.row(Map.of(FeedField.TARGET,"women",FeedField.CATEGORY,"Dresses",FeedField.COLOR,"navy blue/blue/navy",FeedField.MATERIAL,"cotton",FeedField.PATTERN,"plain",FeedField.STYLE_TAGS,"elegant|alien",FeedField.FORMALITY,"unknown")),FeedFixtures.market());
        assertThat(result.target()).isEqualTo(ProductTarget.WOMENSWEAR);assertThat(result.category()).isEqualTo(ProductCategory.DRESS);
        assertThat(result.attributes().values()).containsEntry("primaryColors",List.of("BLUE","NAVY")).containsEntry("materialAppearance",List.of("COTTON_LIKE"))
                .containsEntry("pattern",List.of("SOLID")).containsEntry("styleTags",List.of("ELEGANT")).doesNotContainKey("formality");
        assertThatThrownBy(()->normalizer.normalize(FeedFixtures.row(Map.of(FeedField.TARGET,"unisex")),FeedFixtures.market())).hasMessage("FEED_TARGET_UNSUPPORTED");
        assertThatThrownBy(()->normalizer.normalize(FeedFixtures.row(Map.of(FeedField.CATEGORY,"gadgets")),FeedFixtures.market())).hasMessage("FEED_CATEGORY_UNSUPPORTED");
    }
    @Test void urlsAreValidatedWithoutFetchingAndSensitiveFieldsAreNotPrinted() {
        var market=FeedFixtures.market();
        for(String url:List.of("file:///etc/passwd","https://wrong.example/p","https://example.com.evil.test/p","https://user:password@example.com/p","https://example.com:8443/p"))
            assertThatThrownBy(()->normalizer.normalize(FeedFixtures.row(Map.of(FeedField.PRODUCT_URL,url)),market)).hasMessage("FEED_URL_INVALID");
        var normalized=normalizer.normalize(FeedFixtures.row(Map.of(FeedField.PRODUCT_URL,"HTTPS://EXAMPLE.COM/a/../p?key=a%20b#fragment",FeedField.AFFILIATE_URL,"https://network.example/click?token=private")),market);
        assertThat(normalized.productUrl()).isEqualTo("https://example.com/p?key=a%20b");assertThat(normalized.toString()).doesNotContain("token","private","click");
        assertThatThrownBy(()->normalizer.normalize(FeedFixtures.row(Map.of(FeedField.IMAGE_URL,"javascript:evil")),market)).hasMessage("FEED_URL_INVALID");
    }
    @Test void malformedRowsAndRequiredFieldsRejectSafely() {
        assertThatThrownBy(()->normalizer.normalize(new RawAffiliateFeedRow(2,Map.of(),false),FeedFixtures.market())).hasMessage("FEED_ITEM_INVALID");
        assertThatThrownBy(()->normalizer.normalize(FeedFixtures.row(Map.of(FeedField.TITLE,"")),FeedFixtures.market())).hasMessage("FEED_ITEM_INVALID");
    }
}
