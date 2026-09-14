package com.coupleoutfit.source.application;

import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.source.domain.SourceUrl;
import java.util.UUID;
public interface UrlResolverPort {
    SourceUrl resolve(UUID sourceId, String originalUrl, MarketContext market);
}
