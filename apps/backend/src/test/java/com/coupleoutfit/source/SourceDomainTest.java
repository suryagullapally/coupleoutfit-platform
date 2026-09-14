package com.coupleoutfit.source;

import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SourceDomainTest {
    private final Instant now=Instant.parse("2026-01-01T00:00:00Z");
    private SourceItem item(SourceType type,SourceStatus status) {
        return new SourceItem(UUID.randomUUID(),"a".repeat(64),type,status,new MarketContext("GB","GBP","en-GB"),now,now);
    }
    @Test void imageTransitionsAreExplicitAndTerminal() {
        var pending=item(SourceType.IMAGE_UPLOAD,SourceStatus.AWAITING_UPLOAD);
        assertThatThrownBy(() -> pending.transition(SourceStatus.READY_FOR_ANALYSIS,now)).isInstanceOf(IllegalStateException.class);
        var verifying=pending.transition(SourceStatus.UPLOAD_VERIFYING,now);
        var ready=verifying.transition(SourceStatus.READY_FOR_ANALYSIS,now);
        assertThat(ready.market()).isEqualTo(pending.market());
        assertThatThrownBy(() -> ready.transition(SourceStatus.AWAITING_UPLOAD,now)).isInstanceOf(IllegalStateException.class);
        assertThat(verifying.transition(SourceStatus.FAILED,now).status()).isEqualTo(SourceStatus.FAILED);
    }
    @Test void urlFallbackRetainsSourceIdentityAndContext() {
        var url=item(SourceType.PRODUCT_URL,SourceStatus.RESOLVING_URL);
        var upload=url.transition(SourceStatus.IMAGE_UPLOAD_REQUIRED,now).transition(SourceStatus.AWAITING_UPLOAD,now);
        assertThat(upload.id()).isEqualTo(url.id()); assertThat(upload.sourceType()).isEqualTo(SourceType.PRODUCT_URL);
        assertThat(upload.market()).isEqualTo(url.market());
        assertThatThrownBy(() -> item(SourceType.IMAGE_UPLOAD,SourceStatus.RESOLVING_URL)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void assetVerificationRequiresValidMeasurementsAndOpaqueKey() {
        var asset=SourceAsset.pending(UUID.randomUUID(),now);
        assertThatThrownBy(() -> asset.verified("image/png",0,1,1,"a".repeat(64),now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asset.verified("image/svg+xml",10,1,1,"a".repeat(64),now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> asset.verified("image/png",10,1,1,"not-hash",now)).isInstanceOf(IllegalArgumentException.class);
        var verified=asset.verified("image/png",10,1,1,"a".repeat(64),now);
        assertThatThrownBy(verified::rejected).isInstanceOf(IllegalStateException.class);
        assertThat(verified.toString()).doesNotContain(asset.storageKey(),"a".repeat(64));
    }
    @Test void urlsNormalizeConservativelyAndRejectDangerousInputs() {
        assertThat(SourceUrls.normalize("HTTPS://Shop.Example.com:443/p?id=A%2FB&color=1#detail")).isEqualTo("https://shop.example.com/p?id=A%2FB&color=1");
        assertThat(SourceUrls.normalize("http://example.com:80")).isEqualTo("http://example.com/");
        assertThat(SourceUrls.normalize("https://example.com/p/")).endsWith("/p/");
        for(String url:new String[]{null,"", "file:///x","javascript:alert(1)","data:image/png,a","ftp://example.com/p","https://u:p@example.com/x",
                "http://localhost/a","http://127.0.0.1/a","http://10.0.0.1/a","http://169.254.169.254/a","http://[::1]/a"}) {
            assertThatThrownBy(() -> SourceUrls.normalize(url)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid source URL.");
        }
    }
    @Test void unresolvedUrlHasNoRequiredCatalogueRecord() {
        var url=new SourceUrl(UUID.randomUUID(),"https://example.com/p","https://example.com/p",UrlResolutionStatus.UNSUPPORTED,"CATALOGUE",null,null,null,"SOURCE_URL_UNSUPPORTED",now);
        assertThat(url.resolvedProductOfferId()).isNull();
        assertThatThrownBy(() -> new SourceUrl(url.sourceItemId(),url.originalUrl(),url.normalizedUrl(),UrlResolutionStatus.RESOLVED_CATALOGUE_OFFER,"CATALOGUE",null,null,null,null,now)).isInstanceOf(NullPointerException.class);
    }
}
