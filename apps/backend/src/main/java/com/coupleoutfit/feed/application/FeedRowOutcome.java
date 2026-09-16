package com.coupleoutfit.feed.application;
import com.coupleoutfit.feed.domain.NormalizedAffiliateItem;
public record FeedRowOutcome(long rowNumber,NormalizedAffiliateItem item,String errorCode) {
    public FeedRowOutcome {if(rowNumber<1 || (item==null)==(errorCode==null))throw new IllegalArgumentException("Invalid row outcome.");}
}
