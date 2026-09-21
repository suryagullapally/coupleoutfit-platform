package com.coupleoutfit.affiliate.infrastructure;

import com.coupleoutfit.affiliate.application.PurchaseRepository;
import com.coupleoutfit.affiliate.domain.*;
import com.coupleoutfit.affiliate.domain.AffiliateDestinationPolicy.Destination;
import com.coupleoutfit.market.domain.MarketContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;

@Repository
public class JdbcPurchaseRepository implements PurchaseRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final PurchaseTokens tokens;
    private final AffiliateDestinationPolicy policy;
    private final Clock clock;
    private final Duration lifetime;
    public JdbcPurchaseRepository(NamedParameterJdbcTemplate jdbc,PurchaseTokens tokens,AffiliateDestinationPolicy policy,Clock clock,
            @Value("${coupleoutfit.purchase.lifetime:P30D}") Duration lifetime) {
        this.jdbc=jdbc; this.tokens=tokens; this.policy=policy; this.clock=clock; this.lifetime=lifetime;
        if(lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofDays(90))>0) throw new IllegalArgumentException("Invalid purchase lifetime.");
    }
    private record Context(UUID result,UUID session,UUID offer,UUID market,UUID program,MarketContext marketContext,Destination destination) {}
    private Context context(UUID result) {
        // Match the catalogue/feed lock order: market before offer/product rows.
        jdbc.queryForList("""
            SELECT rm.id FROM match_result mr JOIN product_offer o ON o.id=mr.product_offer_id
            JOIN retailer_market rm ON rm.id=o.retailer_market_id WHERE mr.id=:result FOR SHARE OF rm
            """,Map.of("result",result));
        var contexts=jdbc.query("""
            SELECT mr.id result_id,s.id session_id,o.id offer_id,rm.id market_id,p.id program_id,
                s.country_code,s.currency_code,s.locale_code,o.affiliate_url,rm.domain,p.approved_destination_hosts,
                (o.active AND pr.status='ACTIVE' AND rm.status='ACTIVE' AND r.status='ACTIVE' AND c.active AND cu.active AND l.active) usable,
                p.status program_status,s.status session_status,
                ((s.country_code,s.currency_code,s.locale_code)=(rm.country_code,rm.default_currency_code,rm.locale_code)
                    AND (o.currency_code IS NULL OR o.currency_code=s.currency_code)
                    AND p.retailer_market_id=rm.id) market_valid
            FROM match_result mr JOIN match_session s ON s.id=mr.match_session_id
            JOIN product_offer o ON o.id=mr.product_offer_id JOIN product pr ON pr.id=o.product_id
            JOIN retailer_market rm ON rm.id=o.retailer_market_id JOIN retailer r ON r.id=rm.retailer_id
            JOIN affiliate_feed f ON f.id=o.last_affiliate_feed_id JOIN affiliate_program p ON p.id=f.affiliate_program_id
            JOIN country c ON c.code=rm.country_code JOIN currency cu ON cu.code=rm.default_currency_code JOIN locale l ON l.code=rm.locale_code
            WHERE mr.id=:result
            FOR SHARE OF o,pr,rm,r,f,p,c,cu,l
            """,Map.of("result",result),(row,n)-> {
                if(!row.getBoolean("usable") || !row.getBoolean("market_valid") || !row.getString("session_status").equals("COMPLETED"))
                    throw new PurchaseException("PURCHASE_OFFER_UNAVAILABLE");
                if(!row.getString("program_status").equals("ACTIVE")) throw new PurchaseException("PURCHASE_PROGRAM_UNAVAILABLE");
                var hosts=Set.copyOf(Arrays.asList((String[])row.getArray("approved_destination_hosts").getArray()));
                return new Context(row.getObject("result_id",UUID.class),row.getObject("session_id",UUID.class),row.getObject("offer_id",UUID.class),
                    row.getObject("market_id",UUID.class),row.getObject("program_id",UUID.class),new MarketContext(row.getString("country_code"),row.getString("currency_code"),row.getString("locale_code")),
                    policy.validate(row.getString("affiliate_url"),row.getString("domain"),hosts));
            });
        return contexts.stream().findFirst().orElseThrow(()->new PurchaseException("PURCHASE_DESTINATION_UNAVAILABLE"));
    }
    private PurchaseLink link(ResultSet row)throws SQLException {
        return new PurchaseLink(row.getObject("id",UUID.class),row.getString("token_hash"),row.getString("token_nonce"),row.getObject("match_result_id",UUID.class),
                row.getObject("match_session_id",UUID.class),row.getObject("product_offer_id",UUID.class),PurchaseLinkStatus.valueOf(row.getString("status")),
                row.getTimestamp("expires_at").toInstant(),row.getTimestamp("created_at").toInstant());
    }
    @Override @Transactional public String purchaseUrl(UUID resultId) {
        // Lock only final results; discarded candidates never acquire links.
        if(jdbc.queryForList("SELECT id FROM match_result WHERE id=:result FOR UPDATE",Map.of("result",resultId)).isEmpty()) return null;
        var existing=jdbc.query("SELECT * FROM purchase_link WHERE match_result_id=:result FOR UPDATE",Map.of("result",resultId),(r,n)->link(r));
        Context current;
        try { current=context(resultId); } catch(PurchaseException error) { return null; }
        Instant now=clock.instant();
        PurchaseLink link;
        if(existing.isEmpty()) {
            String nonce=tokens.newNonce();
            link=new PurchaseLink(UUID.randomUUID(),PurchaseTokens.lookupHash(tokens.token(nonce)),nonce,resultId,current.session(),current.offer(),PurchaseLinkStatus.ACTIVE,now.plus(lifetime),now);
            jdbc.update("""
                INSERT INTO purchase_link(id,token_hash,token_nonce,match_result_id,match_session_id,product_offer_id,status,expires_at,created_at)
                VALUES (:id,:hash,:nonce,:result,:session,:offer,'ACTIVE',:expires,:now)
                """,new MapSqlParameterSource().addValue("id",link.id()).addValue("hash",link.tokenHash()).addValue("nonce",link.tokenNonce())
                .addValue("result",resultId).addValue("session",current.session()).addValue("offer",current.offer()).addValue("expires",Timestamp.from(link.expiresAt())).addValue("now",Timestamp.from(now)));
        } else link=existing.getFirst();
        try { link.requireActive(now); } catch(PurchaseException error) { return null; }
        String token=tokens.token(link.tokenNonce());
        // Never issue a different token for an existing result if the operator changes the key.
        if(!PurchaseTokens.lookupHash(token).equals(link.tokenHash())) return null;
        return "/go/"+token;
    }
    @Override @Transactional public Destination recordRedirect(String token,String requestId) {
        String hash=PurchaseTokens.lookupHash(token);
        var found=jdbc.query("SELECT * FROM purchase_link WHERE token_hash=:hash FOR UPDATE",Map.of("hash",hash),(r,n)->link(r));
        var link=found.stream().findFirst().orElseThrow(()->new PurchaseException("PURCHASE_LINK_NOT_FOUND"));
        Instant now=clock.instant(); link.requireActive(now);
        var current=context(link.matchResultId());
        if(!current.offer().equals(link.productOfferId()) || !current.session().equals(link.matchSessionId())) throw new PurchaseException("PURCHASE_OFFER_UNAVAILABLE");
        var click=new AffiliateClick(UUID.randomUUID(),link.id(),current.session(),current.result(),current.offer(),current.market(),current.program(),current.marketContext(),
                requestId,current.destination().host(),current.destination().urlHash(),now);
        jdbc.update("""
            INSERT INTO affiliate_click(id,purchase_link_id,match_session_id,match_result_id,product_offer_id,retailer_market_id,affiliate_program_id,
                country_code,currency_code,locale_code,request_id,destination_host,destination_url_hash,status,created_at,redirected_at)
            VALUES (:id,:link,:session,:result,:offer,:market,:program,:country,:currency,:locale,:request,:host,:hash,'REDIRECTED',:now,:now)
            """,new MapSqlParameterSource().addValue("id",click.id()).addValue("link",click.purchaseLinkId()).addValue("session",click.matchSessionId()).addValue("result",click.matchResultId())
            .addValue("offer",click.productOfferId()).addValue("market",click.retailerMarketId()).addValue("program",click.affiliateProgramId()).addValue("country",click.market().countryCode())
            .addValue("currency",click.market().currencyCode()).addValue("locale",click.market().localeCode()).addValue("request",click.requestId()).addValue("host",click.destinationHost())
            .addValue("hash",click.destinationUrlHash()).addValue("now",Timestamp.from(now)));
        jdbc.update("UPDATE purchase_link SET last_used_at=:now WHERE id=:id",Map.of("id",link.id(),"now",Timestamp.from(now)));
        org.slf4j.LoggerFactory.getLogger(getClass()).info("Affiliate redirect recorded: clickId={} purchaseLinkId={} matchSessionId={} matchResultId={} productOfferId={} retailerMarketId={} destinationHost={} status=REDIRECTED",
                click.id(),click.purchaseLinkId(),click.matchSessionId(),click.matchResultId(),click.productOfferId(),click.retailerMarketId(),click.destinationHost());
        return current.destination();
    }
    @Override @Transactional public void revoke(UUID id) { jdbc.update("UPDATE purchase_link SET status='REVOKED' WHERE id=:id",Map.of("id",id)); }
    @Override @Transactional public void approveDestinationHosts(UUID programId,Set<String> hosts) {
        var approved=policy.approvedHosts(hosts);
        int updated=jdbc.update("UPDATE affiliate_program SET approved_destination_hosts=CAST(:hosts AS text[]),updated_at=GREATEST(updated_at,:now) WHERE id=:id",
                Map.of("id",programId,"hosts","{"+String.join(",",new TreeSet<>(approved))+"}","now",Timestamp.from(clock.instant())));
        if(updated!=1) throw new PurchaseException("PURCHASE_PROGRAM_UNAVAILABLE");
    }
}
