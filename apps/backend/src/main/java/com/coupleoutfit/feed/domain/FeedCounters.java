package com.coupleoutfit.feed.domain;
public record FeedCounters(long itemsRead,long itemsAccepted,long itemsRejected,long productsCreated,long productsUpdated,long offersCreated,long offersUpdated,long offersMarkedStale) {
    public FeedCounters {
        if(itemsRead<0 || itemsAccepted<0 || itemsRejected<0 || productsCreated<0 || productsUpdated<0 || offersCreated<0 || offersUpdated<0 || offersMarkedStale<0
                || itemsAccepted>itemsRead || itemsRejected!=itemsRead-itemsAccepted || productsCreated>itemsAccepted || productsUpdated>itemsAccepted-productsCreated
                || offersCreated>itemsAccepted || offersUpdated>itemsAccepted-offersCreated)throw new IllegalArgumentException("Invalid feed counters.");
    }
    public static FeedCounters empty(){return new FeedCounters(0,0,0,0,0,0,0,0);}
}
