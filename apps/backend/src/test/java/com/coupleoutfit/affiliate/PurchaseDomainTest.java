package com.coupleoutfit.affiliate;

import com.coupleoutfit.affiliate.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PurchaseDomainTest {
    private final AffiliateDestinationPolicy policy=new AffiliateDestinationPolicy();
    @Test void opaqueTokensAreRandomReproducibleAndStoredAsHashes() {
        byte[] key=new byte[32]; new SecureRandom().nextBytes(key); var tokens=new PurchaseTokens(key);
        var values=new HashSet<String>();
        for(int i=0;i<1000;i++) {
            String nonce=tokens.newNonce(),token=tokens.token(nonce);
            assertThat(token).matches("[A-Za-z0-9_-]{43}").doesNotContain(nonce);
            assertThat(tokens.token(nonce)).isEqualTo(token); assertThat(PurchaseTokens.lookupHash(token)).hasSize(64).isNotEqualTo(token);
            values.add(token);
        }
        assertThat(values).hasSize(1000);
        assertThatThrownBy(()->new PurchaseTokens(new byte[8])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->PurchaseTokens.lookupHash("../evil?url=x")).hasMessage("PURCHASE_LINK_NOT_FOUND");
    }
    @Test void expiryRevocationAndSafeStringRepresentation() {
        Instant now=Instant.now();var link=new PurchaseLink(UUID.randomUUID(),"a".repeat(64),"b".repeat(64),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),PurchaseLinkStatus.ACTIVE,now.plusSeconds(5),now);
        link.requireActive(now);assertThatThrownBy(()->link.requireActive(now.plusSeconds(5))).hasMessage("PURCHASE_LINK_EXPIRED");
        var revoked=new PurchaseLink(link.id(),link.tokenHash(),link.tokenNonce(),link.matchResultId(),link.matchSessionId(),link.productOfferId(),PurchaseLinkStatus.REVOKED,link.expiresAt(),now);
        assertThatThrownBy(()->revoked.requireActive(now)).hasMessage("PURCHASE_LINK_REVOKED");
        assertThat(link.toString()).doesNotContain(link.tokenNonce(),link.tokenHash());
    }
    @ParameterizedTest @ValueSource(strings={"javascript:alert(1)","file:///tmp/a","data:text/plain,x","ftp://network.example/a","https://u:p@network.example/a",
        "https://evil.example/a","https://network.example.evil.com/a","https://127.0.0.1/a","https://[::1]/a","https://localhost/a","https://network.example:444/a",
        "https://network.example/a%0d%0aLocation:evil","https://network.example/a#fragment","//network.example/a"})
    void unsafeOrUnapprovedDestinationsAreRejected(String value) {
        assertThatThrownBy(()->policy.validate(value,"example.com",Set.of("network.example"))).hasMessage("PURCHASE_DESTINATION_INVALID");
    }
    @Test void exactNetworkHostsAndRetailerHostsPreserveTrackingBytes() {
        var destination=policy.validate("HTTPS://NETWORK.EXAMPLE/click?q=a%2Fb&sub=abc+123","example.com",Set.of("network.example"));
        assertThat(destination.url()).isEqualTo("https://network.example/click?q=a%2Fb&sub=abc+123");
        assertThat(destination.urlHash()).isEqualTo(PurchaseTokens.hash(destination.url()));
        assertThat(destination.toString()).doesNotContain("sub=", "abc+123");
        assertThat(policy.validate("https://www.example.com/product","example.com",Set.of()).host()).isEqualTo("www.example.com");
        assertThatThrownBy(()->policy.approvedHosts(Set.of("*.example.com"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->policy.approvedHosts(Set.of("metadata.google.internal"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->policy.validate(null,"example.com",Set.of())).hasMessage("PURCHASE_DESTINATION_UNAVAILABLE");
    }
}
