package com.coupleoutfit.affiliate.application;
import com.coupleoutfit.affiliate.domain.*;
import java.util.*;
public interface AffiliateConfigurationRepository {
    void createProgram(AffiliateProgram program);
    void createFeed(AffiliateFeed feed);
    Optional<AffiliateFeed> findFeed(UUID id);
}
