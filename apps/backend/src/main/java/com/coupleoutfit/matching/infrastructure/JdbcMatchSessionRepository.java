package com.coupleoutfit.matching.infrastructure;

import com.coupleoutfit.matching.application.*;
import com.coupleoutfit.matching.application.MatchException;
import com.coupleoutfit.matching.domain.*;
import com.coupleoutfit.analysis.domain.*;
import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class JdbcMatchSessionRepository implements MatchSessionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final MeterRegistry metrics;
    private final JsonMapper json=JsonMapper.builder().build();
    public JdbcMatchSessionRepository(NamedParameterJdbcTemplate jdbc,MeterRegistry metrics){this.jdbc=jdbc;this.metrics=metrics;}
    private static Instant instant(ResultSet r,String field)throws SQLException {var value=r.getTimestamp(field);return value==null?null:value.toInstant();}
    private MatchSession session(ResultSet r,int row)throws SQLException {
        return new MatchSession(r.getObject("id",UUID.class),r.getObject("source_item_id",UUID.class),r.getObject("analysis_revision_id",UUID.class),
                new MarketContext(r.getString("country_code"),r.getString("currency_code"),r.getString("locale_code")),ProductTarget.valueOf(r.getString("partner_target")),
                MatchMode.valueOf(r.getString("match_mode")),r.getBoolean("same_retailer_only"),r.getObject("source_retailer_market_id",UUID.class),MatchStatus.valueOf(r.getString("status")),
                r.getString("algorithm_version"),r.getString("failure_code"),r.getInt("candidate_limit"),r.getInt("result_limit"),r.getInt("minimum_score"),
                json.readValue(r.getString("attributes"),ClothingAttributes.class),instant(r,"created_at"),instant(r,"started_at"),instant(r,"completed_at"),instant(r,"updated_at"));
    }
    private static final String SELECT="SELECT m.*,ar.attributes FROM match_session m JOIN analysis_revision ar ON ar.id=m.analysis_revision_id JOIN source_item s ON s.id=m.source_item_id ";
    @Override public MatchSession findOwned(UUID id,String owner) {
        return jdbc.query(SELECT+"WHERE m.id=:id AND s.owner_session_id=:owner",Map.of("id",id,"owner",owner),this::session).stream().findFirst()
                .orElseThrow(()->new MatchException("MATCH_SESSION_NOT_FOUND"));
    }
    private Optional<MatchSession> existing(String key,String owner) {
        return jdbc.query(SELECT+"WHERE m.request_key=:key AND s.owner_session_id=:owner",Map.of("key",key,"owner",owner),this::session).stream().findFirst();
    }
    private MatchSession matchingRequest(MatchSession s,CreateMatchCommand c) {
        if(!s.sourceItemId().equals(c.sourceItemId()) || !s.analysisRevisionId().equals(c.analysisRevisionId()) || s.partnerTarget()!=c.partnerTarget()
                || s.matchMode()!=c.matchMode() || s.sameRetailerOnly()!=c.sameRetailerOnly())throw new MatchException("MATCH_IDEMPOTENCY_CONFLICT");
        return s;
    }
    @Override @Transactional public MatchSession createOrFind(CreateMatchCommand c,String owner,String key,int candidates,int limit,int minimum,Instant now) {
        var previous=existing(key,owner);if(previous.isPresent())return matchingRequest(previous.get(),c);
        var p=new MapSqlParameterSource().addValue("source",c.sourceItemId()).addValue("owner",owner);
        var sources=jdbc.queryForList("SELECT * FROM source_item WHERE id=:source AND owner_session_id=:owner FOR UPDATE",p);
        if(sources.isEmpty())throw new MatchException("MATCH_SESSION_NOT_FOUND");
        previous=existing(key,owner);if(previous.isPresent())return matchingRequest(previous.get(),c);
        var source=sources.getFirst();
        if(!"READY_FOR_ANALYSIS".equals(source.get("status")))throw new MatchException("MATCH_SOURCE_NOT_READY");
        var analyses=jdbc.queryForList("SELECT * FROM clothing_analysis WHERE source_item_id=:source FOR UPDATE",p);
        if(analyses.isEmpty() || !"COMPLETED".equals(analyses.getFirst().get("status")))throw new MatchException("MATCH_ANALYSIS_NOT_COMPLETED");
        if(!c.analysisRevisionId().equals(analyses.getFirst().get("current_revision_id")))throw new MatchException("MATCH_REVISION_NOT_CURRENT");
        var markets=jdbc.queryForList("""
                SELECT rm.id FROM source_url u JOIN retailer_market rm ON rm.id=u.resolved_retailer_market_id
                WHERE u.source_item_id=:source AND u.resolution_status='RESOLVED_CATALOGUE_OFFER'
                    AND rm.country_code=:country AND rm.default_currency_code=:currency AND rm.locale_code=:locale
                """,p.addValue("country",source.get("country_code")).addValue("currency",source.get("currency_code")).addValue("locale",source.get("locale_code")));
        UUID retailerMarket=markets.isEmpty()?null:(UUID)markets.getFirst().get("id");
        if(c.sameRetailerOnly() && retailerMarket==null)throw new MatchException("SAME_RETAILER_NOT_AVAILABLE");
        p.addValue("id",UUID.randomUUID()).addValue("revision",c.analysisRevisionId()).addValue("key",key).addValue("target",c.partnerTarget().name())
                .addValue("mode",c.matchMode().name()).addValue("same",c.sameRetailerOnly()).addValue("retailerMarket",retailerMarket).addValue("version",RankingAlgorithm.VERSION)
                .addValue("candidates",candidates).addValue("limit",limit).addValue("minimum",minimum).addValue("now",Timestamp.from(now));
        int inserted=jdbc.update("""
                INSERT INTO match_session(id,source_item_id,analysis_revision_id,request_key,country_code,currency_code,locale_code,
                    partner_target,match_mode,same_retailer_only,source_retailer_market_id,status,algorithm_version,candidate_limit,result_limit,minimum_score,created_at,updated_at)
                VALUES (:id,:source,:revision,:key,:country,:currency,:locale,:target,:mode,:same,:retailerMarket,'CREATED',:version,:candidates,:limit,:minimum,:now,:now)
                ON CONFLICT (request_key) DO NOTHING
                """,p);
        if(inserted==1)metrics.counter("match.session.created","mode",c.matchMode().name(),"sameRetailer",Boolean.toString(c.sameRetailerOnly())).increment();
        return matchingRequest(existing(key,owner).orElseThrow(),c);
    }
    @Override public AnalysisRevision revision(UUID id) {
        return jdbc.query("SELECT ar.* FROM analysis_revision ar JOIN match_session m ON m.analysis_revision_id=ar.id WHERE m.id=:id",Map.of("id",id),(r,n)->
                new AnalysisRevision(r.getObject("id",UUID.class),r.getObject("clothing_analysis_id",UUID.class),r.getInt("revision_number"),RevisionType.valueOf(r.getString("revision_type")),
                        r.getObject("parent_revision_id",UUID.class),json.readValue(r.getString("attributes"),ClothingAttributes.class),
                        r.getString("confidence")==null?null:json.readValue(r.getString("confidence"),AttributeConfidence.class),r.getString("schema_version"),r.getString("provider"),
                        r.getString("model"),r.getString("provider_request_id"),r.getString("prompt_version"),instant(r,"created_at"))).getFirst();
    }
    @Override @Transactional public boolean claim(UUID id,Instant now) {
        return jdbc.update("UPDATE match_session SET status='MATCHING',started_at=:now,updated_at=:now WHERE id=:id AND status='CREATED'",Map.of("id",id,"now",Timestamp.from(now)))==1;
    }
    @Override @Transactional public void complete(UUID id,List<MatchResult> results,Instant now) {
        var states=jdbc.queryForList("SELECT status,started_at FROM match_session WHERE id=:id FOR UPDATE",Map.of("id",id));
        if(states.isEmpty() || !"MATCHING".equals(states.getFirst().get("status")))throw new MatchException("MATCH_ALREADY_FINALIZED");
        for(var r:results) {
            if(!id.equals(r.matchSessionId()))throw new IllegalArgumentException("Result belongs to another session.");
            var b=r.breakdown();
            jdbc.update("""
                    INSERT INTO match_result(id,match_session_id,product_offer_id,rank,couple_match_score,score_band,semantic_score,color_score,style_score,
                        formality_score,pattern_score,seasonality_score,category_score,explanation,algorithm_version,created_at)
                    VALUES (:id,:session,:offer,:rank,:score,:band,:semantic,:color,:style,:formality,:pattern,:seasonality,:category,CAST(:explanation AS jsonb),:version,:now)
                    """,new MapSqlParameterSource().addValue("id",r.id()).addValue("session",id).addValue("offer",r.productOfferId()).addValue("rank",r.rank())
                    .addValue("score",r.coupleMatchScore().value()).addValue("band",r.scoreBand().name()).addValue("semantic",b.semanticScore()).addValue("color",b.colorScore())
                    .addValue("style",b.styleScore()).addValue("formality",b.formalityScore()).addValue("pattern",b.patternScore()).addValue("seasonality",b.seasonalityScore())
                    .addValue("category",b.categoryScore()).addValue("explanation",json.writeValueAsString(r.explanation())).addValue("version",r.algorithmVersion()).addValue("now",Timestamp.from(r.createdAt())));
        }
        jdbc.update("UPDATE match_session SET status='COMPLETED',completed_at=:now,updated_at=:now WHERE id=:id",Map.of("id",id,"now",Timestamp.from(now)));
        metrics.counter("match.session.completed").increment();if(results.isEmpty())metrics.counter("match.session.zero_results").increment();
        metrics.summary("match.result.count").record(results.size());
        metrics.timer("match.duration").record(Duration.between(((Timestamp)states.getFirst().get("started_at")).toInstant(),now));
        org.slf4j.LoggerFactory.getLogger(getClass()).atInfo().addKeyValue("matchSessionId",id).addKeyValue("resultCount",results.size()).log("Match completed");
    }
    @Override @Transactional public void fail(UUID id,String code,Instant now) {
        if(jdbc.update("UPDATE match_session SET status='FAILED',failure_code=:code,completed_at=:now,updated_at=:now WHERE id=:id AND status='MATCHING'",Map.of("id",id,"code",code,"now",Timestamp.from(now)))==1)
            metrics.counter("match.session.failed","code",code).increment();
    }
    @Override public List<MatchResultView> results(UUID id) {
        return List.copyOf(jdbc.query("""
                SELECT mr.*,p.id AS product_id,p.canonical_name,p.brand,p.category,r.id AS retailer_id,r.slug,r.name,
                    o.amount_minor,o.currency_code,o.availability_status,o.primary_image_url AS image_url
                FROM match_result mr JOIN product_offer o ON o.id=mr.product_offer_id JOIN product p ON p.id=o.product_id
                    JOIN retailer_market rm ON rm.id=o.retailer_market_id JOIN retailer r ON r.id=rm.retailer_id
                WHERE mr.match_session_id=:id ORDER BY mr.rank
                """,Map.of("id",id),(r,n)->new MatchResultView(
                    new MatchResult(r.getObject("id",UUID.class),id,r.getObject("product_offer_id",UUID.class),r.getInt("rank"),new CoupleMatchScore(r.getInt("couple_match_score")),ScoreBand.valueOf(r.getString("score_band")),
                        new CompatibilityScoreBreakdown(r.getInt("semantic_score"),r.getInt("color_score"),r.getInt("style_score"),r.getInt("formality_score"),r.getInt("pattern_score"),r.getInt("seasonality_score"),r.getInt("category_score")),
                        json.readValue(r.getString("explanation"),MatchExplanation.class),r.getString("algorithm_version"),instant(r,"created_at")),
                    new MatchResultView.Product(r.getObject("product_id",UUID.class),r.getString("canonical_name"),r.getString("brand"),ProductCategory.valueOf(r.getString("category"))),
                    new MatchResultView.Retailer(r.getObject("retailer_id",UUID.class),r.getString("slug"),r.getString("name")),
                    new MatchResultView.Offer(r.getObject("product_offer_id",UUID.class),r.getObject("amount_minor",Long.class),r.getString("currency_code"),AvailabilityStatus.valueOf(r.getString("availability_status")),r.getString("image_url")))));
    }
}
