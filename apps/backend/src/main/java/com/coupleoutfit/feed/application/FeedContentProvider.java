package com.coupleoutfit.feed.application;
import com.coupleoutfit.affiliate.domain.AffiliateFeed;
import java.io.InputStream;
public interface FeedContentProvider { InputStream open(AffiliateFeed feed); }
