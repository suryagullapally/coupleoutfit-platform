package com.coupleoutfit.feed.infrastructure;

import com.coupleoutfit.affiliate.application.AffiliateConfigurationRepository;
import com.coupleoutfit.feed.application.*;
import com.coupleoutfit.feed.domain.*;
import com.coupleoutfit.catalogue.application.*;
import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
public class JdbcFeedRunRepository implements FeedRunRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final AffiliateConfigurationRepository definitions;
    private final CatalogueRepository catalogue;
    private final CatalogueWriter writer;
    private final MeterRegistry metrics;
    private final JsonMapper json=JsonMapper.builder().build();
    public JdbcFeedRunRepository(NamedParameterJdbcTemplate jdbc,AffiliateConfigurationRepository definitions,CatalogueRepository catalogue,CatalogueWriter writer,MeterRegistry metrics) {
        this.jdbc=jdbc;this.definitions=definitions;this.catalogue=catalogue;this.writer=writer;this.metrics=metrics;
    }
    private static Timestamp stamp(Instant instant){return Timestamp.from(instant);}
    @Override @Transactional public FeedRunContext start(UUID feedId,Instant now) {
        var keys=Map.of("feed",feedId);
        if(jdbc.queryForList("SELECT id FROM affiliate_feed WHERE id=:feed FOR UPDATE",keys).isEmpty())throw new FeedException("FEED_NOT_FOUND");
        var feed=definitions.findFeed(feedId).orElseThrow(()->new FeedException("FEED_NOT_FOUND"));
        var markets=jdbc.query("""
                SELECT rm.id,rm.retailer_id,rm.country_code,rm.default_currency_code,rm.locale_code,cu.minor_unit_digits,rm.domain
                FROM affiliate_feed f JOIN affiliate_program p ON p.id=f.affiliate_program_id
                JOIN retailer_market rm ON rm.id=f.retailer_market_id JOIN retailer r ON r.id=rm.retailer_id
                JOIN country c ON c.code=rm.country_code JOIN currency cu ON cu.code=rm.default_currency_code JOIN locale l ON l.code=rm.locale_code
                WHERE f.id=:feed AND f.status='ACTIVE' AND p.status='ACTIVE' AND rm.status='ACTIVE' AND r.status='ACTIVE'
                    AND rm.catalogue_enabled AND c.active AND cu.active AND l.active
                """,keys,(r,n)->new FeedMarket(r.getObject("id",UUID.class),r.getObject("retailer_id",UUID.class),new MarketContext(r.getString("country_code"),r.getString("default_currency_code"),r.getString("locale_code")),r.getInt("minor_unit_digits"),r.getString("domain")));
        if(markets.isEmpty())throw new FeedException("FEED_DISABLED");
        if(!jdbc.queryForList("SELECT id FROM affiliate_feed_run WHERE affiliate_feed_id=:feed AND status IN ('STARTED','PROCESSING')",keys).isEmpty())throw new FeedException("FEED_RUN_ALREADY_ACTIVE");
        var id=UUID.randomUUID();var p=new MapSqlParameterSource().addValue("id",id).addValue("feed",feedId).addValue("now",stamp(now));
        jdbc.update("INSERT INTO affiliate_feed_run(id,affiliate_feed_id,status,started_at,created_at) VALUES (:id,:feed,'STARTED',:now,:now)",p);
        jdbc.update("UPDATE affiliate_feed SET last_attempted_run_at=:now,updated_at=GREATEST(updated_at,:now) WHERE id=:feed",p);
        metrics.counter("feed.run.started","format",feed.format().name()).increment();
        return new FeedRunContext(feed,markets.getFirst(),find(id));
    }
    @Override @Transactional public void processing(UUID id) {
        var run=lock(id);run.requireTransition(FeedRunStatus.PROCESSING);
        jdbc.update("UPDATE affiliate_feed_run SET status='PROCESSING' WHERE id=:id",Map.of("id",id));
    }
    private AffiliateFeedRun lock(UUID id) {
        jdbc.queryForList("SELECT id FROM affiliate_feed_run WHERE id=:id FOR UPDATE",Map.of("id",id));return find(id);
    }
    @Override @Transactional public long applyBatch(FeedRunContext context,List<FeedRowOutcome> rows,Instant now) {
        if(rows.isEmpty() || rows.size()>1000)throw new IllegalArgumentException("Invalid batch size.");
        var run=lock(context.run().id());if(run.status()!=FeedRunStatus.PROCESSING || !run.affiliateFeedId().equals(context.feed().id()))throw new FeedException("FEED_RUN_FAILED");
        // Serializes first inserts with other feeds and the existing catalogue writer's natural-key lock.
        jdbc.queryForList("SELECT id FROM retailer_market WHERE id=:market FOR UPDATE",Map.of("market",context.market().retailerMarketId()));
        long accepted=0,rejected=0,pc=0,pu=0,oc=0,ou=0;var errors=new HashMap<>(run.rejectionCounts());
        for(var row:rows) {
            String error=row.errorCode();
            if(error!=null){rejected++;errors.merge(error,1L,Long::sum);continue;}
            var item=row.item();var existing=catalogue.findOfferByMarketAndRetailerProductId(context.market().retailerMarketId(),item.retailerProductId());
            if(existing.isPresent()) {
                var ownership=jdbc.queryForList("SELECT last_affiliate_feed_id FROM product_offer WHERE id=:id",Map.of("id",existing.get().id()));
                var owner=ownership.getFirst().get("last_affiliate_feed_id");
                if(owner!=null && !owner.equals(context.feed().id())) {rejected++;errors.merge("FEED_OFFER_OWNED_BY_ANOTHER_FEED",1L,Long::sum);continue;}
            }
            Product product;
            if(existing.isEmpty()) {
                product=writer.createProduct(new Product(UUID.randomUUID(),context.market().retailerId(),item.title(),item.brand(),item.category(),null,item.target(),item.attributes(),"1",ProductStatus.ACTIVE,now,now));pc++;
            } else {
                product=catalogue.findProduct(existing.get().productId()).orElseThrow(()->new FeedException("FEED_RUN_FAILED"));
                var desired=new Product(product.id(),product.retailerId(),item.title(),item.brand(),item.category(),null,item.target(),item.attributes(),"1",ProductStatus.ACTIVE,product.createdAt(),product.updatedAt());
                if(!desired.equals(product)) {
                    Instant changed=now.truncatedTo(ChronoUnit.MICROS);if(!changed.isAfter(product.updatedAt()))changed=product.updatedAt().plus(1,ChronoUnit.MICROS);
                    product=writer.updateProduct(new Product(desired.id(),desired.retailerId(),desired.canonicalName(),desired.brand(),desired.category(),desired.subcategory(),desired.target(),desired.attributes(),desired.attributeSchemaVersion(),desired.status(),desired.createdAt(),changed));pu++;
                }
            }
            var old=existing.orElse(null);Instant updated=old==null?now:now.isBefore(old.updatedAt())?old.updatedAt():now;
            var offer=writer.upsertOffer(new ProductOffer(old==null?UUID.randomUUID():old.id(),product.id(),context.market().retailerMarketId(),item.retailerProductId(),
                    item.title(),item.productUrl(),item.affiliateUrl(),item.price(),item.availability(),item.imageUrl(),true,run.startedAt(),now,old==null?now:old.createdAt(),updated));
            jdbc.update("UPDATE product_offer SET last_affiliate_feed_id=:feed,last_seen_feed_run_id=:run WHERE id=:id",Map.of("feed",context.feed().id(),"run",run.id(),"id",offer.id()));
            accepted++;if(old==null)oc++;else ou++;
        }
        jdbc.update("""
                UPDATE affiliate_feed_run SET items_read=items_read+:read,items_accepted=items_accepted+:accepted,items_rejected=items_rejected+:rejected,
                    products_created=products_created+:pc,products_updated=products_updated+:pu,offers_created=offers_created+:oc,offers_updated=offers_updated+:ou,rejection_counts=CAST(:errors AS jsonb)
                WHERE id=:id
                """,new MapSqlParameterSource().addValue("id",run.id()).addValue("read",rows.size()).addValue("accepted",accepted).addValue("rejected",rejected)
                .addValue("pc",pc).addValue("pu",pu).addValue("oc",oc).addValue("ou",ou).addValue("errors",json.writeValueAsString(errors)));
        metrics.counter("feed.items.read").increment(rows.size());metrics.counter("feed.items.accepted").increment(accepted);metrics.counter("feed.items.rejected").increment(rejected);
        metrics.counter("feed.products.created").increment(pc);metrics.counter("feed.products.updated").increment(pu);metrics.counter("feed.offers.created").increment(oc);metrics.counter("feed.offers.updated").increment(ou);
        return run.counters().itemsRejected()+rejected;
    }
    @Override @Transactional public AffiliateFeedRun complete(UUID id,Instant now) {
        var run=lock(id);var status=run.counters().itemsRejected()==0?FeedRunStatus.COMPLETED:FeedRunStatus.COMPLETED_WITH_ERRORS;run.requireTransition(status);
        int stale=0;
        // Empty or partially rejected snapshots are never authoritative enough for absence reconciliation.
        if(status==FeedRunStatus.COMPLETED && run.counters().itemsAccepted()>0) {
            stale=jdbc.update("""
                    UPDATE product_offer SET availability_status='UNKNOWN',updated_at=GREATEST(updated_at,:now)
                    WHERE last_affiliate_feed_id=:feed AND last_seen_feed_run_id<>:id AND active AND availability_status='IN_STOCK'
                    """,Map.of("id",id,"feed",run.affiliateFeedId(),"now",stamp(now)));
        }
        jdbc.update("UPDATE affiliate_feed_run SET status=:status,completed_at=:now,offers_marked_stale=:stale WHERE id=:id",Map.of("id",id,"status",status.name(),"now",stamp(now),"stale",stale));
        if(status==FeedRunStatus.COMPLETED)jdbc.update("UPDATE affiliate_feed SET last_successful_run_at=:now,updated_at=GREATEST(updated_at,:now) WHERE id=:feed",Map.of("feed",run.affiliateFeedId(),"now",stamp(now)));
        return observed(find(id));
    }
    @Override @Transactional public AffiliateFeedRun fail(UUID id,String code,Instant now) {
        var run=lock(id);run.requireTransition(FeedRunStatus.FAILED);
        jdbc.update("UPDATE affiliate_feed_run SET status='FAILED',failure_code=:code,completed_at=:now WHERE id=:id",Map.of("id",id,"code",code,"now",stamp(now)));
        return observed(find(id));
    }
    private AffiliateFeedRun observed(AffiliateFeedRun run) {
        metrics.counter(run.status()==FeedRunStatus.FAILED?"feed.run.failed":"feed.run.completed","status",run.status().name()).increment();
        metrics.timer("feed.duration").record(Duration.between(run.startedAt(),run.completedAt()));
        org.slf4j.LoggerFactory.getLogger(getClass()).info(
                "Feed run finished: feedRunId={} affiliateFeedId={} retailerMarketId={} itemsRead={} itemsAccepted={} itemsRejected={} durationMs={} status={} failureCode={}",
                run.id(),run.affiliateFeedId(),definitions.findFeed(run.affiliateFeedId()).orElseThrow().retailerMarketId(),
                run.counters().itemsRead(),run.counters().itemsAccepted(),run.counters().itemsRejected(),
                Duration.between(run.startedAt(),run.completedAt()).toMillis(),run.status(),run.failureCode());
        return run;
    }
    @Override public AffiliateFeedRun find(UUID id) {
        return jdbc.query("SELECT * FROM affiliate_feed_run WHERE id=:id",Map.of("id",id),(r,n)->new AffiliateFeedRun(r.getObject("id",UUID.class),r.getObject("affiliate_feed_id",UUID.class),FeedRunStatus.valueOf(r.getString("status")),
                new FeedCounters(r.getLong("items_read"),r.getLong("items_accepted"),r.getLong("items_rejected"),r.getLong("products_created"),r.getLong("products_updated"),r.getLong("offers_created"),r.getLong("offers_updated"),r.getLong("offers_marked_stale")),
                json.readValue(r.getString("rejection_counts"),new TypeReference<Map<String,Long>>(){}),r.getString("failure_code"),r.getTimestamp("started_at").toInstant(),r.getTimestamp("completed_at")==null?null:r.getTimestamp("completed_at").toInstant(),r.getTimestamp("created_at").toInstant()))
                .stream().findFirst().orElseThrow(()->new FeedException("FEED_NOT_FOUND"));
    }
}
