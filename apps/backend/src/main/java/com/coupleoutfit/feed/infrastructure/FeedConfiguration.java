package com.coupleoutfit.feed.infrastructure;
import com.coupleoutfit.feed.application.*;
import com.coupleoutfit.feed.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.nio.file.Path;
import java.time.Clock;
@Configuration(proxyBeanMethods=false)
public class FeedConfiguration {
    @Bean FeedContentProvider feedContentProvider(Environment environment,
            @Value("${coupleoutfit.feed.local-root:.local/affiliate-feeds}") String root,
            @Value("${coupleoutfit.feed.max-bytes:67108864}") long maxBytes) {
        if(environment.matchesProfiles("local | test") && !environment.matchesProfiles("production | staging"))
            return new LocalFileFeedContentProvider(Path.of(root),maxBytes);
        return feed->{throw new FeedException("FEED_SOURCE_UNAVAILABLE");};
    }
    @Bean AffiliateFeedReader affiliateFeedReader(){return new CsvAffiliateFeedReader();}
    @Bean FeedImportService feedImportService(FeedRunRepository repository,FeedContentProvider content,AffiliateFeedReader reader,Clock clock,
            @Value("${coupleoutfit.feed.batch-size:500}") int batchSize,
            @Value("${coupleoutfit.feed.max-rejected-rows:1000}") int maxRejectedRows) {
        return new FeedImportService(repository,content,reader,new FeedItemNormalizer(),clock,batchSize,maxRejectedRows);
    }
}
