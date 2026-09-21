package com.coupleoutfit.affiliate;

import com.coupleoutfit.affiliate.application.*;
import com.coupleoutfit.affiliate.domain.*;
import com.coupleoutfit.analysis.AnalysisFixtures;
import com.coupleoutfit.analysis.application.ClothingAnalysisRepository;
import com.coupleoutfit.catalogue.domain.ProductTarget;
import com.coupleoutfit.feed.FeedFixtures;
import com.coupleoutfit.feed.application.FeedImportService;
import com.coupleoutfit.matching.application.*;
import com.coupleoutfit.matching.domain.*;
import com.coupleoutfit.source.api.AnonymousSourceSession;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers @ExtendWith(OutputCaptureExtension.class)
class PurchaseIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres"));
    static final Path ROOT=temporaryRoot();
    static Path temporaryRoot(){try{return Files.createTempDirectory("purchase-feed-");}catch(IOException e){throw new UncheckedIOException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("coupleoutfit.feed.local-root",ROOT::toString);}
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired PurchaseLinks purchases;@Autowired PurchaseTokens tokens;
    @Autowired AffiliateConfigurationRepository definitions;@Autowired FeedImportService imports;@Autowired ClothingAnalysisRepository analyses;
    @Autowired MatchSessionRepository matches;
    private final JsonMapper json=JsonMapper.builder().build();
    record Fixture(UUID retailer,UUID market,UUID program,UUID offer,UUID session,UUID result,Cookie cookie) {}
    @AfterEach void cleanup()throws IOException {
        jdbc.update("DELETE FROM affiliate_click");jdbc.update("DELETE FROM purchase_link");jdbc.update("DELETE FROM match_result");jdbc.update("DELETE FROM match_session");
        jdbc.update("DELETE FROM product_offer");jdbc.update("DELETE FROM product");jdbc.update("DELETE FROM affiliate_feed_run");jdbc.update("DELETE FROM affiliate_feed");jdbc.update("DELETE FROM affiliate_program");
        jdbc.update("DELETE FROM retailer_market");jdbc.update("DELETE FROM retailer");
        try(var paths=Files.list(ROOT)){for(var path:paths.toList())Files.delete(path);}
    }
    @AfterAll static void removeRoot()throws IOException {Files.delete(ROOT);}
    private Fixture fixture()throws Exception {
        UUID retailer=UUID.randomUUID(),market=UUID.randomUUID();
        jdbc.update("INSERT INTO retailer VALUES (?,?,'Purchase fixture','ACTIVE',now(),now())",retailer,"purchase-"+retailer);
        jdbc.update("INSERT INTO retailer_market VALUES (?,?,'GB','GBP','en-GB','example.com','ACTIVE',true,true,now(),now())",market,retailer);
        var program=FeedFixtures.program(market,AffiliateProgramStatus.ACTIVE);var feed=FeedFixtures.feed(program,"fixture.csv",FeedSchema.genericCsv());
        definitions.createProgram(program);definitions.createFeed(feed);purchases.approveDestinationHosts(program.id(),Set.of("network.example"));
        try(var input=getClass().getResourceAsStream("/feeds/valid.csv")){Files.copy(Objects.requireNonNull(input),ROOT.resolve("fixture.csv"));}
        imports.run(feed.id());
        UUID offer=jdbc.queryForObject("SELECT id FROM product_offer WHERE retailer_market_id=? AND retailer_product_id='C'",UUID.class,market);
        var response=mvc.perform(post("/api/v1/source-items").header("X-Source-Request","1").contentType("application/json").content("{\"sourceType\":\"IMAGE_UPLOAD\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var source=UUID.fromString(json.readTree(response.getContentAsString()).path("sourceItemId").asString());
        var cookie=new Cookie(AnonymousSourceSession.COOKIE,response.getHeader("Set-Cookie").split(";",2)[0].substring((AnonymousSourceSession.COOKIE+"=").length()));
        jdbc.update("UPDATE source_item SET status='READY_FOR_ANALYSIS' WHERE id=?",source);
        var claim=analyses.claim(source,Instant.now());analyses.complete(claim.snapshot().analysis().id(),AnalysisFixtures.result(),Instant.now());
        var revision=analyses.findBySource(source).orElseThrow().currentRevision().id();
        String owner=jdbc.queryForObject("SELECT owner_session_id FROM source_item WHERE id=?",String.class,source);
        var session=matches.createOrFind(new CreateMatchCommand(source,revision,ProductTarget.MENSWEAR,MatchMode.COORDINATED,false),owner,UUID.randomUUID().toString().replace("-","").repeat(2),50,12,60,Instant.now());
        matches.claim(session.id(),Instant.now());UUID result=UUID.randomUUID();var score=new CoupleMatchScore(90);
        matches.complete(session.id(),List.of(new MatchResult(result,session.id(),offer,1,score,score.band(),new CompatibilityScoreBreakdown(90,90,90,90,90,90,90),new MatchExplanation(List.of("Coordinated fixture.")),RankingAlgorithm.VERSION,Instant.now())),Instant.now());
        return new Fixture(retailer,market,program.id(),offer,session.id(),result,cookie);
    }
    private JsonNode response(Fixture f)throws Exception {
        String body=mvc.perform(get("/api/v1/match-sessions/"+f.session()).cookie(f.cookie())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("affiliateUrl","network.example","click?id", "tokenHash", "tokenNonce");return json.readTree(body);
    }
    private String url(Fixture f)throws Exception {return response(f).path("results").get(0).path("purchase").path("url").asString();}
    private int clicks(){return jdbc.queryForObject("SELECT count(*) FROM affiliate_click",Integer.class);}
    @Test void ownedResponseReusesLinkAndAnonymousRedirectPersistsMinimalAttribution(CapturedOutput output)throws Exception {
        var f=fixture();String url=url(f);assertThat(url).matches("/go/[A-Za-z0-9_-]{43}");assertThat(url(f)).isEqualTo(url);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_link",Integer.class)).isEqualTo(1);
        String token=url.substring(4),stored=jdbc.queryForObject("SELECT token_hash FROM purchase_link",String.class);
        assertThat(stored).isEqualTo(PurchaseTokens.lookupHash(token)).isNotEqualTo(token);
        mvc.perform(get("/api/v1/match-sessions/"+f.session())).andExpect(status().isNotFound());
        var sourceId=response(f).path("sourceItemId").asString();
        mvc.perform(get("/api/v1/source-items/"+sourceId).cookie(f.cookie())).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("affiliateUrl"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("network.example"))));
        for(int i=0;i<2;i++)mvc.perform(get(url).header("X-Request-Id","purchase-test").header("Referer","https://private.example/path?secret=x").header("User-Agent","Fixture browser"))
            .andExpect(status().isFound()).andExpect(header().string("Location","https://network.example/click?id=C"))
            .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Referrer-Policy","no-referrer"))
            .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("X-Request-Id","purchase-test")).andExpect(content().string(""));
        assertThat(clicks()).isEqualTo(2);
        var row=jdbc.queryForMap("SELECT * FROM affiliate_click LIMIT 1");assertThat(row).containsEntry("match_session_id",f.session()).containsEntry("match_result_id",f.result())
            .containsEntry("product_offer_id",f.offer()).containsEntry("retailer_market_id",f.market()).containsEntry("affiliate_program_id",f.program())
            .containsEntry("country_code","GB").containsEntry("currency_code","GBP").containsEntry("locale_code","en-GB").containsEntry("request_id","purchase-test")
            .containsEntry("destination_host","network.example").containsEntry("destination_url_hash",PurchaseTokens.hash("https://network.example/click?id=C"));
        assertThat(row.keySet()).doesNotContain("ip_hash","user_agent_hash","referrer_host","affiliate_url");
        assertThat(output.getOut()).contains("/go/[redacted]").doesNotContain(token,"https://network.example/click?id=C");
    }
    @Test void encodedRoutePathsCannotLeakPurchaseTokensToLogs(CapturedOutput output)throws Exception {
        String token=url(fixture()).substring(4);
        mvc.perform(get(java.net.URI.create("/%67o/"+token)));
        assertThat(output.getOut()).contains("/go/[redacted]").doesNotContain(token);
    }
    @Test void arbitraryDestinationInputNeverOverridesServerDestination()throws Exception {
        var f=fixture();String url=url(f);
        mvc.perform(get(url).queryParam("url","https://evil.example").queryParam("destination","javascript:bad"))
            .andExpect(status().isFound()).andExpect(header().string("Location","https://network.example/click?id=C"));
        mvc.perform(get("/go/not-a-token")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PURCHASE_LINK_NOT_FOUND"));
        mvc.perform(get(url+"/evil")).andExpect(status().isNotFound()).andExpect(header().doesNotExist("Location"));
        assertThat(clicks()).isEqualTo(1);
    }
    @Test void currentUrlAvailabilityAndPriceDoNotChangeIdentity()throws Exception {
        var f=fixture();String url=url(f);
        jdbc.update("UPDATE product_offer SET affiliate_url='https://network.example/new?track=a%2Fb&sub=123',availability_status='OUT_OF_STOCK' WHERE id=?",f.offer());
        mvc.perform(get(url)).andExpect(status().isFound()).andExpect(header().string("Location","https://network.example/new?track=a%2Fb&sub=123"));
        assertThat(url(f)).isEqualTo(url);assertThat(response(f).path("results").get(0).path("offer").path("amountMinor").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT destination_url_hash FROM affiliate_click",String.class)).isEqualTo(PurchaseTokens.hash("https://network.example/new?track=a%2Fb&sub=123"));
    }
    @Test void pausedDisabledProgramsMarketsAndOffersStopExistingLinks()throws Exception {
        var f=fixture();String url=url(f);
        for(String state:List.of("PAUSED","DISABLED")) {
            jdbc.update("UPDATE affiliate_program SET status=? WHERE id=?",state,f.program());
            mvc.perform(get(url)).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PURCHASE_PROGRAM_UNAVAILABLE"));
            assertThat(response(f).path("results").get(0).path("purchase").path("available").asBoolean()).isFalse();
        }
        jdbc.update("UPDATE affiliate_program SET status='ACTIVE' WHERE id=?",f.program());
        jdbc.update("UPDATE retailer_market SET status='INACTIVE' WHERE id=?",f.market());mvc.perform(get(url)).andExpect(status().isGone());
        jdbc.update("UPDATE retailer_market SET status='ACTIVE' WHERE id=?",f.market());
        jdbc.update("UPDATE product_offer SET active=false WHERE id=?",f.offer());mvc.perform(get(url)).andExpect(status().isGone());assertThat(clicks()).isZero();
    }
    @Test void missingOrUnsafeDestinationNeverCreatesDeadTokenOrFallsBack()throws Exception {
        var f=fixture();
        for(String value:Arrays.asList(null,"https://unknown.example/product","https://u:p@network.example/x")) {
            jdbc.update("UPDATE product_offer SET affiliate_url=? WHERE id=?",value,f.offer());
            var purchase=response(f).path("results").get(0).path("purchase");assertThat(purchase.path("available").asBoolean()).isFalse();assertThat(purchase.path("url").isNull()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_link",Integer.class)).isZero();
        jdbc.update("UPDATE product_offer SET affiliate_url='https://example.com/direct?affiliate=1' WHERE id=?",f.offer());String url=url(f);
        mvc.perform(get(url)).andExpect(status().isFound());
        jdbc.update("UPDATE product_offer SET affiliate_url='https://unknown.example/product' WHERE id=?",f.offer());
        mvc.perform(get(url)).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PURCHASE_DESTINATION_INVALID")).andExpect(header().doesNotExist("Location"));
    }
    @Test void headBotsAndPrefetchDoNotCountOrRedirect()throws Exception {
        String url=url(fixture());
        mvc.perform(head(url)).andExpect(status().isNoContent()).andExpect(header().doesNotExist("Location"));
        mvc.perform(get(url).header("User-Agent","Googlebot/2.1")).andExpect(status().isNoContent());
        mvc.perform(get(url).header("Sec-Purpose","prefetch;prerender")).andExpect(status().isNoContent());assertThat(clicks()).isZero();
    }
    @Test void expiredRevokedAndUnknownTokensFailSafely()throws Exception {
        var f=fixture();String url=url(f);UUID id=jdbc.queryForObject("SELECT id FROM purchase_link",UUID.class);
        purchases.revoke(id);mvc.perform(get(url)).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PURCHASE_LINK_REVOKED"));
        assertThat(response(f).path("results").get(0).path("purchase").path("available").asBoolean()).isFalse();
        jdbc.update("UPDATE purchase_link SET status='ACTIVE',created_at=now()-interval '2 days',expires_at=now()-interval '1 day' WHERE id=?",id);
        mvc.perform(get(url)).andExpect(status().isGone()).andExpect(jsonPath("$.code").value("PURCHASE_LINK_EXPIRED")).andExpect(jsonPath("$.instance").value("/go"));
        mvc.perform(get("/go/"+tokens.token(tokens.newNonce()))).andExpect(status().isNotFound());assertThat(clicks()).isZero();
    }
    @Test void trackingFailureRollsBackAndNeverRedirects()throws Exception {
        String url=url(fixture());
        jdbc.execute("CREATE FUNCTION reject_test_click() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test persistence failure'; END; $$");
        jdbc.execute("CREATE TRIGGER reject_test_click BEFORE INSERT ON affiliate_click FOR EACH ROW EXECUTE FUNCTION reject_test_click()");
        try {
            mvc.perform(get(url)).andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("PURCHASE_TRACKING_FAILED")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))));
            assertThat(clicks()).isZero();assertThat(jdbc.queryForObject("SELECT last_used_at IS NULL FROM purchase_link",Boolean.class)).isTrue();
        } finally {jdbc.execute("DROP TRIGGER reject_test_click ON affiliate_click");jdbc.execute("DROP FUNCTION reject_test_click()");}
    }
    @Test void concurrentGenerationReusesOneLinkAndClicksRemainSeparate()throws Exception {
        var f=fixture();
        try(var executor=Executors.newFixedThreadPool(6)) {
            var requests=new ArrayList<Callable<String>>();for(int i=0;i<12;i++)requests.add(()->purchases.forResult(f.result()).url());
            var urls=new HashSet<String>();for(var result:executor.invokeAll(requests))urls.add(result.get(10,TimeUnit.SECONDS));assertThat(urls).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_link",Integer.class)).isEqualTo(1);
            String token=urls.iterator().next().substring(4);var clicks=new ArrayList<Callable<String>>();
            for(int i=0;i<6;i++)clicks.add(()->purchases.redirect(token,"concurrent").host());
            for(var result:executor.invokeAll(clicks))assertThat(result.get(10,TimeUnit.SECONDS)).isEqualTo("network.example");
        }
        assertThat(clicks()).isEqualTo(6);
    }
    @Test void historicalMarketIsPreservedAndCrossMarketMutationBlocksRedirect()throws Exception {
        var f=fixture();String url=url(f);mvc.perform(get(url)).andExpect(status().isFound());
        jdbc.update("INSERT INTO country(code,name,active) VALUES ('IN','India',true) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO currency(code,minor_unit_digits,active) VALUES ('INR',2,true) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO locale(code,country_code,default_currency_code,active) VALUES ('en-IN','IN','INR',true) ON CONFLICT DO NOTHING");
        jdbc.update("UPDATE product_offer SET amount_minor=NULL,currency_code=NULL WHERE retailer_market_id=?",f.market());
        jdbc.update("UPDATE retailer_market SET country_code='IN',default_currency_code='INR',locale_code='en-IN' WHERE id=?",f.market());
        mvc.perform(get(url)).andExpect(status().isGone());
        assertThat(jdbc.queryForObject("SELECT country_code FROM affiliate_click",String.class)).isEqualTo("GB");assertThat(clicks()).isEqualTo(1);
    }
    @Test void migrationUpgradeUniquenessForeignKeysAndHistoryRetention()throws Exception {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6","7","8","9");
        var config=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).defaultSchema("purchase_upgrade").schemas("purchase_upgrade");
        config.target("8").load().migrate();var prior=jdbc.queryForList("SELECT checksum FROM purchase_upgrade.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",Integer.class);
        assertThat(prior).containsExactly(-929483003,-362163080,-360589483,-599056603,85417613,-976671557,-1417818194,-881954208);
        config.target("9").load().migrate();config.load().validate();assertThat(jdbc.queryForList("SELECT checksum FROM purchase_upgrade.flyway_schema_history WHERE version IS NOT NULL AND version<>'9' ORDER BY installed_rank",Integer.class)).isEqualTo(prior);
        var f=fixture();String url=url(f);mvc.perform(get(url)).andExpect(status().isFound());
        assertThatThrownBy(()->jdbc.update("INSERT INTO purchase_link SELECT gen_random_uuid(),token_hash,token_nonce,match_result_id,match_session_id,product_offer_id,status,expires_at,created_at,last_used_at FROM purchase_link")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM product_offer WHERE id=?",f.offer())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM purchase_link")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE affiliate_click SET destination_host='evil.example'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO affiliate_click SELECT gen_random_uuid(),gen_random_uuid(),match_session_id,match_result_id,product_offer_id,retailer_market_id,affiliate_program_id,country_code,currency_code,locale_code,request_id,destination_host,destination_url_hash,status,created_at,redirected_at FROM affiliate_click")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(clicks()).isEqualTo(1);
    }
}
