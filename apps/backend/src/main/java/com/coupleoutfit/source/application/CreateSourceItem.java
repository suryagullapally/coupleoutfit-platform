package com.coupleoutfit.source.application;

import com.coupleoutfit.market.application.MarketContextResolver;
import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import java.time.Clock;
import java.util.UUID;

public final class CreateSourceItem {
    public record Result(SourceSnapshot source, SourceAssetStorage.UploadAuthorization upload) {}
    private final SourceItemRepository repository;
    private final MarketContextResolver market;
    private final UrlResolverPort resolver;
    private final SourceAssetStorage storage;
    private final Clock clock;
    public CreateSourceItem(SourceItemRepository repository, MarketContextResolver market, UrlResolverPort resolver, SourceAssetStorage storage, Clock clock) {
        this.repository=repository; this.market=market; this.resolver=resolver; this.storage=storage; this.clock=clock;
    }
    public Result create(String owner, SourceType type, String url) {
        if(type==null || type==SourceType.IMAGE_UPLOAD && url!=null || type==SourceType.PRODUCT_URL && url==null) throw new SourceException("SOURCE_REQUEST_INVALID");
        var now=clock.instant(); var id=UUID.randomUUID(); var context=market.resolve();
        var item=new SourceItem(id,owner,type,type==SourceType.IMAGE_UPLOAD?SourceStatus.AWAITING_UPLOAD:SourceStatus.RESOLVING_URL,context,now,now);
        if(type==SourceType.IMAGE_UPLOAD) {
            var asset=SourceAsset.pending(id,now);
            var authorization=storage.createUploadAuthorization(asset.storageKey());
            return new Result(repository.create(item,asset,null),authorization);
        }
        var resolved=resolver.resolve(id,url,context);
        item=item.transition(resolved.resolutionStatus()==UrlResolutionStatus.RESOLVED_CATALOGUE_OFFER?SourceStatus.READY_FOR_ANALYSIS:SourceStatus.IMAGE_UPLOAD_REQUIRED,clock.instant());
        return new Result(repository.create(item,null,resolved),null);
    }
    public Result authorizeFallback(UUID id, String owner) {
        var current=repository.findOwned(id,owner);
        if(current.item().status()==SourceStatus.AWAITING_UPLOAD && current.asset()!=null) {
            return new Result(current,storage.createUploadAuthorization(current.asset().storageKey()));
        }
        if(current.item().status()!=SourceStatus.IMAGE_UPLOAD_REQUIRED) throw new SourceException("SOURCE_INVALID_STATE");
        var pending=repository.startFallback(id,owner,clock.instant());
        return new Result(pending,storage.createUploadAuthorization(pending.asset().storageKey()));
    }
}
