package com.coupleoutfit.source.application;

import com.coupleoutfit.catalogue.application.CatalogueRepository;
import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.retailer.application.RetailerRepository;
import com.coupleoutfit.source.domain.*;
import java.time.Clock;
import java.util.UUID;

/** Resolves trusted persisted data only; never makes an outbound network request. */
public final class CatalogueOfferUrlResolver implements UrlResolverPort {
    private final CatalogueRepository catalogue;
    private final RetailerRepository retailers;
    private final Clock clock;
    public CatalogueOfferUrlResolver(CatalogueRepository catalogue, RetailerRepository retailers, Clock clock) {
        this.catalogue=catalogue; this.retailers=retailers; this.clock=clock;
    }
    @Override public SourceUrl resolve(UUID id, String original, MarketContext market) {
        final String normalized;
        try { normalized=SourceUrls.normalize(original); } catch (IllegalArgumentException e) { throw new SourceException("SOURCE_URL_INVALID"); }
        var matches=catalogue.findActiveOffers(market).stream().filter(offer -> normalized.equals(safeNormalize(offer.productUrl())))
                .filter(offer -> safeNormalize(offer.primaryImageUrl()) != null).toList();
        // Ambiguous catalogue URLs must not choose an arbitrary product.
        if (matches.size()==1) {
            var offer=matches.getFirst();
            return new SourceUrl(id,original,normalized,UrlResolutionStatus.RESOLVED_CATALOGUE_OFFER,"CATALOGUE",
                    offer.retailerMarketId(),offer.id(),offer.primaryImageUrl(),null,clock.instant());
        }
        String host=java.net.URI.create(normalized).getHost();
        var retailer=retailers.findActiveMarkets(market).stream().filter(m -> m.domain().equals(host)).findFirst();
        return new SourceUrl(id,original,normalized,UrlResolutionStatus.UNSUPPORTED,"CATALOGUE",
                retailer.map(m -> m.id()).orElse(null),null,null,
                retailer.isPresent()?"SUPPORTED_RETAILER_URL_NOT_RESOLVABLE":"SOURCE_URL_UNSUPPORTED",clock.instant());
    }
    private static String safeNormalize(String value) {
        try { return SourceUrls.normalize(value); } catch (IllegalArgumentException e) { return null; }
    }
}
