package com.coupleoutfit.affiliate.infrastructure;

import com.coupleoutfit.affiliate.application.*;
import com.coupleoutfit.affiliate.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.security.SecureRandom;
import java.util.Base64;

@Configuration(proxyBeanMethods=false)
public class PurchaseConfiguration {
    @Bean PurchaseTokens purchaseTokens(Environment environment,@Value("${coupleoutfit.purchase.token-key:}") String encoded) {
        byte[] key;
        if(encoded.isBlank() && environment.matchesProfiles("local | test") && !environment.matchesProfiles("production | staging")) {
            key=new byte[32]; new SecureRandom().nextBytes(key);
        } else {
            try { key=Base64.getDecoder().decode(encoded); }
            catch(IllegalArgumentException error) { throw new IllegalStateException("Purchase token key must be Base64 encoded."); }
            if(key.length<32) throw new IllegalStateException("Configure a purchase token key of at least 32 random bytes.");
        }
        return new PurchaseTokens(key);
    }
    @Bean AffiliateDestinationPolicy affiliateDestinationPolicy() { return new AffiliateDestinationPolicy(); }
    @Bean PurchaseLinks purchaseLinks(PurchaseRepository repository) { return new PurchaseLinks(repository); }
}
