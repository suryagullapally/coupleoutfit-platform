package com.coupleoutfit.feed.application;
import com.coupleoutfit.affiliate.domain.AffiliateFeed;
import com.coupleoutfit.feed.domain.*;
public record FeedRunContext(AffiliateFeed feed,FeedMarket market,AffiliateFeedRun run) {}
