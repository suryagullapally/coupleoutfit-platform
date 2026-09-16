package com.coupleoutfit.feed.domain;
import com.coupleoutfit.affiliate.domain.FeedField;
import java.util.*;
public record RawAffiliateFeedRow(long rowNumber,Map<FeedField,String> values,boolean validShape) {
    public RawAffiliateFeedRow {if(rowNumber<1)throw new IllegalArgumentException("Invalid row number.");values=Map.copyOf(values);}
    @Override public String toString(){return "RawAffiliateFeedRow[rowNumber="+rowNumber+"]";}
}
