package com.coupleoutfit.affiliate.domain;
import java.time.Instant;
import java.util.*;
public record AffiliateProgram(UUID id,UUID retailerMarketId,AffiliateNetwork network,String externalProgramId,String name,
        AffiliateProgramStatus status,Instant createdAt,Instant updatedAt) {
    public AffiliateProgram {
        Objects.requireNonNull(id);Objects.requireNonNull(retailerMarketId);Objects.requireNonNull(network);Objects.requireNonNull(status);
        name=AffiliateValidation.text(name,160,true);externalProgramId=AffiliateValidation.text(externalProgramId,200,false);
        AffiliateValidation.times(createdAt,updatedAt);
    }
    public boolean active(){return status==AffiliateProgramStatus.ACTIVE;}
}
