package com.coupleoutfit.feed.infrastructure;
import com.coupleoutfit.feed.application.FeedImportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.UUID;
/** Explicit one-shot development execution; never enabled just by selecting the local profile. */
@Component
@Profile("local & !production & !staging")
@ConditionalOnProperty("coupleoutfit.feed.local-run-id")
public final class LocalFeedRunner implements ApplicationRunner {
    private final FeedImportService imports;private final UUID feedId;
    public LocalFeedRunner(FeedImportService imports,@Value("${coupleoutfit.feed.local-run-id}") UUID feedId){this.imports=imports;this.feedId=feedId;}
    @Override public void run(ApplicationArguments arguments){imports.run(feedId);}
}
