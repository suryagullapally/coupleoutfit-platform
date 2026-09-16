package com.coupleoutfit.affiliate.infrastructure;
import com.coupleoutfit.affiliate.application.AffiliateConfigurationRepository;
import com.coupleoutfit.affiliate.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.type.TypeReference;
import java.sql.*;
import java.time.Instant;
import java.util.*;
@Repository
public class JdbcAffiliateConfigurationRepository implements AffiliateConfigurationRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    public JdbcAffiliateConfigurationRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override @Transactional public void createProgram(AffiliateProgram p) {
        jdbc.update("""
                INSERT INTO affiliate_program(id,retailer_market_id,network,external_program_id,name,status,created_at,updated_at)
                VALUES (:id,:market,:network,:external,:name,:status,:created,:updated)
                """,new MapSqlParameterSource().addValue("id",p.id()).addValue("market",p.retailerMarketId()).addValue("network",p.network().name())
                .addValue("external",p.externalProgramId()).addValue("name",p.name()).addValue("status",p.status().name()).addValue("created",Timestamp.from(p.createdAt())).addValue("updated",Timestamp.from(p.updatedAt())));
    }
    @Override @Transactional public void createFeed(AffiliateFeed f) {
        var programs=jdbc.query("SELECT * FROM affiliate_program WHERE id=:id",Map.of("id",f.affiliateProgramId()),(r,n)->
                new AffiliateProgram(r.getObject("id",UUID.class),r.getObject("retailer_market_id",UUID.class),AffiliateNetwork.valueOf(r.getString("network")),r.getString("external_program_id"),r.getString("name"),AffiliateProgramStatus.valueOf(r.getString("status")),time(r,"created_at"),time(r,"updated_at")));
        if(programs.isEmpty())throw new IllegalArgumentException("Programme not found.");f.validateFor(programs.getFirst());
        jdbc.update("""
                INSERT INTO affiliate_feed(id,affiliate_program_id,retailer_market_id,external_feed_id,name,format,status,source_type,source_location,mapping_config,created_at,updated_at)
                VALUES (:id,:program,:market,:external,:name,:format,:status,:sourceType,:location,CAST(:mapping AS jsonb),:created,:updated)
                """,new MapSqlParameterSource().addValue("id",f.id()).addValue("program",f.affiliateProgramId()).addValue("market",f.retailerMarketId())
                .addValue("external",f.externalFeedId()).addValue("name",f.name()).addValue("format",f.format().name()).addValue("status",f.status().name())
                .addValue("sourceType",f.sourceType().name()).addValue("location",f.sourceLocation()).addValue("mapping",json.writeValueAsString(f.schema().columns()))
                .addValue("created",Timestamp.from(f.createdAt())).addValue("updated",Timestamp.from(f.updatedAt())));
    }
    @Override public Optional<AffiliateFeed> findFeed(UUID id) {
        return jdbc.query("SELECT * FROM affiliate_feed WHERE id=:id",Map.of("id",id),(r,n)->new AffiliateFeed(r.getObject("id",UUID.class),r.getObject("affiliate_program_id",UUID.class),
                r.getObject("retailer_market_id",UUID.class),r.getString("external_feed_id"),r.getString("name"),FeedFormat.valueOf(r.getString("format")),AffiliateFeedStatus.valueOf(r.getString("status")),
                FeedSourceType.valueOf(r.getString("source_type")),r.getString("source_location"),new FeedSchema(json.readValue(r.getString("mapping_config"),new TypeReference<Map<FeedField,String>>(){})),
                time(r,"last_attempted_run_at"),time(r,"last_successful_run_at"),time(r,"created_at"),time(r,"updated_at"))).stream().findFirst();
    }
    private static Instant time(ResultSet r,String field)throws SQLException{var t=r.getTimestamp(field);return t==null?null:t.toInstant();}
}
