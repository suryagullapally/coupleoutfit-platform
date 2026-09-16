package com.coupleoutfit.feed.application;
import com.coupleoutfit.affiliate.domain.FeedSchema;
import com.coupleoutfit.feed.domain.RawAffiliateFeedRow;
import java.io.InputStream;
import java.util.Optional;
public interface AffiliateFeedReader {
    Cursor open(InputStream content,FeedSchema schema);
    interface Cursor extends AutoCloseable {
        Optional<RawAffiliateFeedRow> next();
        @Override void close();
    }
}
