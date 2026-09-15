package com.coupleoutfit.matching.application;
import com.coupleoutfit.catalogue.domain.ProductTarget;
import com.coupleoutfit.matching.domain.MatchMode;
import java.util.UUID;
public record CreateMatchCommand(UUID sourceItemId,UUID analysisRevisionId,ProductTarget partnerTarget,MatchMode matchMode,boolean sameRetailerOnly) {
    public CreateMatchCommand {
        if(sourceItemId==null || analysisRevisionId==null || partnerTarget==null)throw new MatchException("MATCH_REQUEST_INVALID");
        if(matchMode==null)matchMode=MatchMode.COORDINATED;
    }
}
