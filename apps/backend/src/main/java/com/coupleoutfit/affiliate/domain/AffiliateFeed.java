package com.coupleoutfit.affiliate.domain;
import java.time.Instant;
import java.util.*;
public record AffiliateFeed(UUID id,UUID affiliateProgramId,UUID retailerMarketId,String externalFeedId,String name,FeedFormat format,
        AffiliateFeedStatus status,FeedSourceType sourceType,String sourceLocation,FeedSchema schema,Instant lastAttemptedRunAt,Instant lastSuccessfulRunAt,Instant createdAt,Instant updatedAt) {
    public AffiliateFeed {
        Objects.requireNonNull(id);Objects.requireNonNull(affiliateProgramId);Objects.requireNonNull(retailerMarketId);Objects.requireNonNull(format);
        Objects.requireNonNull(status);Objects.requireNonNull(sourceType);Objects.requireNonNull(schema);
        name=AffiliateValidation.text(name,160,true);externalFeedId=AffiliateValidation.text(externalFeedId,200,false);
        sourceLocation=AffiliateValidation.text(sourceLocation,255,true);
        if(!sourceLocation.matches("[A-Za-z0-9_/-]+\\.csv") || sourceLocation.startsWith("/") || Arrays.asList(sourceLocation.split("/")).contains(".."))throw new IllegalArgumentException("Feed location must be a relative CSV reference.");
        AffiliateValidation.times(createdAt,updatedAt);
    }
    public void validateFor(AffiliateProgram program) {
        if(!affiliateProgramId.equals(program.id()) || !retailerMarketId.equals(program.retailerMarketId()))throw new IllegalArgumentException("Feed market differs from its programme.");
    }
    public boolean active(){return status==AffiliateFeedStatus.ACTIVE;}
    @Override public String toString(){return "AffiliateFeed[id="+id+", retailerMarketId="+retailerMarketId+"]";}
}
