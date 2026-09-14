package com.coupleoutfit.retailer.api;

import com.coupleoutfit.retailer.application.RetailerQueries;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RetailerController {
    private final RetailerQueries queries;
    public RetailerController(RetailerQueries queries) { this.queries = queries; }
    @GetMapping("/api/v1/retailers")
    public RetailersResponse retailers() {
        return new RetailersResponse(queries.activeMarkets().stream().map(m -> new RetailerResponse(
                m.retailer().id(), m.retailer().slug(), m.retailer().name(), new MarketResponse(m.country().code()))).toList());
    }
    public record RetailersResponse(List<RetailerResponse> retailers) {}
    public record RetailerResponse(UUID id, String slug, String name, MarketResponse market) {}
    public record MarketResponse(String countryCode) {}
}
