package com.coupleoutfit.feed;
import com.coupleoutfit.affiliate.domain.*;
import com.coupleoutfit.feed.domain.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class FeedDomainTest {
    @Test void programAndFeedValidateMarketConfiguration() {
        var market=UUID.randomUUID();var program=FeedFixtures.program(market,AffiliateProgramStatus.ACTIVE);
        var feed=FeedFixtures.feed(program,"fixture.csv",FeedSchema.genericCsv());feed.validateFor(program);assertThat(program.active()).isTrue();assertThat(feed.active()).isTrue();
        assertThat(FeedFixtures.program(market,AffiliateProgramStatus.PAUSED).active()).isFalse();assertThat(FeedFixtures.program(market,AffiliateProgramStatus.DISABLED).active()).isFalse();
        assertThatThrownBy(()->feed.validateFor(FeedFixtures.program(UUID.randomUUID(),AffiliateProgramStatus.ACTIVE))).isInstanceOf(IllegalArgumentException.class);
        for(String location:List.of("../private.csv","/tmp/file.csv","https://example.com/feed.csv","foo/../../private.csv","secret?token.csv"))
            assertThatThrownBy(()->FeedFixtures.feed(program,location,FeedSchema.genericCsv())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void mappingRejectsIncompleteDuplicateOrUnboundedColumns() {
        assertThatThrownBy(()->new FeedSchema(Map.of())).isInstanceOf(IllegalArgumentException.class);
        var map=new EnumMap<>(FeedSchema.genericCsv().columns());map.put(FeedField.TITLE,"retailer_product_id");assertThatThrownBy(()->new FeedSchema(map)).isInstanceOf(IllegalArgumentException.class);
        map.put(FeedField.TITLE,"title");map.remove(FeedField.CURRENCY);assertThatThrownBy(()->new FeedSchema(map)).isInstanceOf(IllegalArgumentException.class);
        map.remove(FeedField.PRICE);assertThat(new FeedSchema(map).columns()).doesNotContainKey(FeedField.PRICE);
    }
    @Test void runTransitionsAndCounterInvariants() {
        var now=Instant.now();var started=new AffiliateFeedRun(UUID.randomUUID(),UUID.randomUUID(),FeedRunStatus.STARTED,FeedCounters.empty(),Map.of(),null,now,null,now);
        started.requireTransition(FeedRunStatus.PROCESSING);started.requireTransition(FeedRunStatus.FAILED);
        assertThatThrownBy(()->started.requireTransition(FeedRunStatus.COMPLETED)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->new FeedCounters(1,2,0,0,0,0,0,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new FeedCounters(1,0,1,1,0,0,0,0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AffiliateFeedRun(started.id(),started.affiliateFeedId(),FeedRunStatus.COMPLETED,FeedCounters.empty(),Map.of(),null,now,null,now)).isInstanceOf(IllegalArgumentException.class);
        var completed=new AffiliateFeedRun(started.id(),started.affiliateFeedId(),FeedRunStatus.COMPLETED,FeedCounters.empty(),Map.of(),null,now,now,now);
        assertThatThrownBy(()->completed.requireTransition(FeedRunStatus.PROCESSING)).isInstanceOf(IllegalStateException.class);
    }
}
