package com.coupleoutfit.feed.domain;
import com.coupleoutfit.catalogue.domain.*;
import java.util.Objects;
public record NormalizedAffiliateItem(String retailerProductId,String title,String brand,ProductCategory category,ProductTarget target,
        ProductAttributes attributes,String productUrl,String affiliateUrl,String imageUrl,Money price,AvailabilityStatus availability) {
    public NormalizedAffiliateItem {
        Objects.requireNonNull(retailerProductId);Objects.requireNonNull(title);Objects.requireNonNull(category);Objects.requireNonNull(target);
        Objects.requireNonNull(attributes);Objects.requireNonNull(productUrl);Objects.requireNonNull(availability);
    }
    @Override public String toString(){return "NormalizedAffiliateItem[category="+category+", target="+target+"]";}
}
