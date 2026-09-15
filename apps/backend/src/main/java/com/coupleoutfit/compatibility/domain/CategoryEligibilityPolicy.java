package com.coupleoutfit.compatibility.domain;
import com.coupleoutfit.catalogue.domain.ProductCategory;
import java.util.*;
/** Broad clothing retrieval, with accessories excluded from primary results. No styling rules. */
public final class CategoryEligibilityPolicy {
    public Set<ProductCategory> allowedFor(ProductCategory source) {
        Objects.requireNonNull(source);var allowed=EnumSet.allOf(ProductCategory.class);allowed.remove(ProductCategory.ACCESSORY);return Set.copyOf(allowed);
    }
}
