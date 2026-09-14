package com.coupleoutfit.market.api;

import com.coupleoutfit.market.application.MarketContextResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarketContextController {
    private final MarketContextResolver resolver;
    public MarketContextController(MarketContextResolver resolver) { this.resolver = resolver; }
    @GetMapping("/api/v1/context")
    public ContextResponse context() {
        var market = resolver.resolve();
        return new ContextResponse(new MarketResponse(market.countryCode(), market.currencyCode(), market.localeCode()));
    }
    public record ContextResponse(MarketResponse market) {}
    public record MarketResponse(String countryCode, String currencyCode, String localeCode) {}
}
