package com.coupleoutfit.feed;
import com.coupleoutfit.affiliate.domain.*;
import com.coupleoutfit.feed.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import java.time.Instant;
import java.util.*;
public final class FeedFixtures {
    private FeedFixtures() {}
    public static FeedMarket market(){return new FeedMarket(UUID.randomUUID(),UUID.randomUUID(),new MarketContext("GB","GBP","en-GB"),2,"example.com");}
    public static AffiliateProgram program(UUID market,AffiliateProgramStatus status){var now=Instant.now();return new AffiliateProgram(UUID.randomUUID(),market,AffiliateNetwork.GENERIC,"programme","Fixture",status,now,now);}
    public static AffiliateFeed feed(AffiliateProgram program,String file,FeedSchema schema) {
        var now=Instant.now();return new AffiliateFeed(UUID.randomUUID(),program.id(),program.retailerMarketId(),null,"Fixture",FeedFormat.CSV,AffiliateFeedStatus.ACTIVE,FeedSourceType.LOCAL_FILE,file,schema,null,null,now,now);
    }
    public static RawAffiliateFeedRow row(Map<FeedField,String> override) {
        var fields=new EnumMap<FeedField,String>(FeedField.class);
        fields.put(FeedField.RETAILER_PRODUCT_ID,"item-1");fields.put(FeedField.TITLE,"Navy shirt");fields.put(FeedField.CATEGORY,"shirt");fields.put(FeedField.TARGET,"men");
        fields.put(FeedField.PRODUCT_URL,"https://example.com/p");fields.put(FeedField.PRICE,"49.99");fields.put(FeedField.CURRENCY,"GBP");fields.putAll(override);
        return new RawAffiliateFeedRow(1,fields,true);
    }
}
