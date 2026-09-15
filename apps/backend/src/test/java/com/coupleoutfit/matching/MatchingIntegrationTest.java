package com.coupleoutfit.matching;

import com.coupleoutfit.matching.application.*;
import com.coupleoutfit.matching.domain.*;
import com.coupleoutfit.compatibility.application.*;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.catalogue.application.*;
import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.analysis.application.*;
import com.coupleoutfit.analysis.domain.*;
import com.coupleoutfit.analysis.AnalysisFixtures;
import com.coupleoutfit.source.api.AnonymousSourceSession;
import com.coupleoutfit.storage.application.SourceAssetStorage;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"coupleoutfit.ai.embeddings.retry-backoff=PT0S","coupleoutfit.ai.clothing-analysis.retry-backoff=PT0S"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class MatchingIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres"));
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired CatalogueWriter catalogue;
    @Autowired EnsureProductEmbedding products;@Autowired ClothingAnalysisRepository analyses;
    @Autowired MatchSessions matches;@Autowired MatchSessionRepository sessions;@Autowired SourceAssetStorage storage;
    @Autowired EmbeddingModelMetadata model;
    @MockitoBean EmbeddingProvider embeddings;@MockitoBean ClothingAnalysisPort vision;
    private final JsonMapper json=JsonMapper.builder().build();
    record Source(UUID id,UUID revision,Cookie cookie,String owner) {}
    record Fixture(UUID retailer,UUID market,Product product,UUID offer,String url) {}
    private ClothingAttributes attributes() {
        var a=AnalysisFixtures.attributes();return new ClothingAttributes(a.category(),a.subcategory(),List.of("BLUE"),List.of(),a.pattern(),List.of("UNKNOWN"),a.fit(),a.formality(),List.of("WINTER"),a.styleTags(),a.details());
    }
    private ClothingAnalysisProviderResult analysisResult(){return new ClothingAnalysisProviderResult(attributes(),AnalysisFixtures.result().confidence(),"OPENAI","test-vision",null,"clothing-analysis-v1");}
    private EmbeddingProvider.Result vector() {var values=new ArrayList<Float>(Collections.nCopies(1536,0f));values.set(0,1f);return new EmbeddingProvider.Result(new EmbeddingVector(values),model,null);}
    @BeforeEach void fake() {
        when(embeddings.embed(any())).thenAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return vector();});
        when(vision.analyse(any())).thenReturn(analysisResult());
    }
    @AfterEach void cleanup() {
        jdbc.queryForList("SELECT storage_key FROM source_asset",String.class).forEach(storage::deleteObject);
        jdbc.update("DELETE FROM match_result");jdbc.update("DELETE FROM match_session");jdbc.update("DELETE FROM source_url");
        jdbc.update("DELETE FROM product_embedding");jdbc.update("DELETE FROM product_offer");jdbc.update("DELETE FROM product");jdbc.update("DELETE FROM retailer_market");jdbc.update("DELETE FROM retailer");
    }
    private Fixture fixture(String color,String pattern,String country,UUID existingRetailer) {
        String currency=country.equals("GB")?"GBP":"INR",locale=country.equals("GB")?"en-GB":"en-IN";
        if(country.equals("IN")) {
            jdbc.update("INSERT INTO country(code,name,active) VALUES ('IN','India',true) ON CONFLICT DO NOTHING");
            jdbc.update("INSERT INTO currency(code,minor_unit_digits,active) VALUES ('INR',2,true) ON CONFLICT DO NOTHING");
            jdbc.update("INSERT INTO locale(code,country_code,default_currency_code,active) VALUES ('en-IN','IN','INR',true) ON CONFLICT DO NOTHING");
        }
        UUID retailer=existingRetailer==null?UUID.randomUUID():existingRetailer,market=UUID.randomUUID();var now=Instant.now();
        if(existingRetailer==null)jdbc.update("INSERT INTO retailer VALUES (?,?,'Match fixture','ACTIVE',now(),now())",retailer,"matching-"+retailer);
        var host="r-"+market+".example.com";
        jdbc.update("INSERT INTO retailer_market VALUES (?,?,?,?,?,?,'ACTIVE',true,true,now(),now())",market,retailer,country,currency,locale,host);
        var p=catalogue.createProduct(new Product(UUID.randomUUID(),retailer,"Match fixture",null,ProductCategory.DRESS,null,ProductTarget.MENSWEAR,
                new ProductAttributes(Map.of("primaryColors",List.of(color),"pattern",List.of(pattern),"formality",List.of("SMART"),"styleTags",List.of("ELEGANT"),"seasonality",List.of("WINTER"))),"1",ProductStatus.ACTIVE,now,now));
        String url="https://"+host+"/p";
        var offer=catalogue.upsertOffer(new ProductOffer(UUID.randomUUID(),p.id(),market,"fixture","Match fixture",url,"https://private.example/affiliate",new Money(4999,currency),AvailabilityStatus.IN_STOCK,"https://"+host+"/image.png",true,now,now,now,now));
        products.ensure(p.id());return new Fixture(retailer,market,p,offer.id(),url);
    }
    private Source source(String url,boolean ready) throws Exception {
        String body=url==null?"{\"sourceType\":\"IMAGE_UPLOAD\"}":json.writeValueAsString(Map.of("sourceType","PRODUCT_URL","url",url));
        var response=mvc.perform(post("/api/v1/source-items").header("X-Source-Request","1").contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse();
        var data=json.readTree(response.getContentAsString());var id=UUID.fromString(data.path("sourceItemId").asString());
        assertThat(response.getHeader("Set-Cookie")).contains("Path=/api/v1;","HttpOnly","SameSite=Strict");
        var token=response.getHeader("Set-Cookie").split(";",2)[0].substring((AnonymousSourceSession.COOKIE+"=").length());var cookie=new Cookie(AnonymousSourceSession.COOKIE,token);
        if(ready && url==null) {
            try(var image=getClass().getResourceAsStream("/images/tiny.png")){mvc.perform(put(data.path("upload").path("uploadUrl").asString()).contentType("image/png").content(Objects.requireNonNull(image).readAllBytes())).andExpect(status().isNoContent());}
            mvc.perform(post("/api/v1/source-items/"+id+"/upload-complete").cookie(cookie).header("X-Source-Request","1").contentType("application/json").content(json.writeValueAsString(Map.of("assetId",data.path("asset").path("assetId").asString())))).andExpect(status().isOk());
            mvc.perform(post("/api/v1/source-items/"+id+"/analysis").cookie(cookie).header("X-Source-Request","1")).andExpect(status().isOk());
        } else if(ready) {var claim=analyses.claim(id,Instant.now());analyses.complete(claim.snapshot().analysis().id(),analysisResult(),Instant.now());}
        return new Source(id,ready?analyses.findBySource(id).orElseThrow().currentRevision().id():UUID.randomUUID(),cookie,jdbc.queryForObject("SELECT owner_session_id FROM source_item WHERE id=?",String.class,id));
    }
    private CreateMatchCommand command(Source s,MatchMode mode,boolean same){return new CreateMatchCommand(s.id(),s.revision(),ProductTarget.MENSWEAR,mode,same);}
    private JsonNode create(Source s,MatchMode mode,boolean same,UUID key)throws Exception {
        var response=mvc.perform(post("/api/v1/match-sessions").cookie(s.cookie()).header("X-Source-Request","1").header("Idempotency-Key",key).header("X-Request-Id","matching-test")
                .contentType("application/json").content(json.writeValueAsString(command(s,mode,same)))).andExpect(status().isOk()).andExpect(header().string("X-Request-Id","matching-test"))
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("affiliate","private.example","semanticScore","breakdown","owner_session","embedding");
        return json.readTree(response.getContentAsString());
    }
    @Test void endToEndModesMissingPriceCommercialIndependenceAndHistory() throws Exception {
        var same=fixture("BLUE","SOLID","GB",null);var neutral=fixture("WHITE","STRIPED","GB",null);var contrasting=fixture("ORANGE","STRIPED","GB",null);
        var s=source(null,true);UUID exactId=null;
        for(var mode:MatchMode.values()) {
            var body=create(s,mode,false,UUID.randomUUID());assertThat(body.path("status").asString()).isEqualTo("COMPLETED");assertThat(body.path("results").size()).isEqualTo(3);
            UUID expected=switch(mode){case EXACT->same.offer();case COORDINATED->neutral.offer();case COMPLEMENTARY->contrasting.offer();};
            assertThat(body.path("results").get(0).path("offer").path("offerId").asString()).isEqualTo(expected.toString());
            assertThat(body.path("market").path("countryCode").asString()).isEqualTo("GB");
            assertThat(body.path("results").get(0).path("explanation").path("reasons").size()).isEqualTo(3);
            if(mode==MatchMode.EXACT)exactId=UUID.fromString(body.path("id").asString());
        }
        var before=sessions.results(exactId);
        jdbc.update("UPDATE product_offer SET amount_minor=NULL,currency_code=NULL,affiliate_url=NULL WHERE id=?",same.offer());
        var after=create(s,MatchMode.EXACT,false,UUID.randomUUID());
        assertThat(after.path("results").get(0).path("coupleMatchScore").asInt()).isEqualTo(before.getFirst().result().coupleMatchScore().value());
        assertThat(after.path("results").get(0).path("offer").path("amountMinor").isNull()).isTrue();
        assertThat(after.path("results").get(0).path("offer").path("currencyCode").isNull()).isTrue();
        jdbc.update("UPDATE product SET brand='Changed brand',attributes='{}',updated_at=now() WHERE id=?",same.product().id());
        assertThat(sessions.results(exactId).stream().map(MatchResultView::result).toList()).isEqualTo(before.stream().map(MatchResultView::result).toList());
        mvc.perform(get("/api/v1/match-sessions/"+exactId).cookie(s.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].product.brand").value("Changed brand"));
    }
    @Test void sameRetailerMarketFilterBeforeLimitAndCrossMarketIsolation() throws Exception {
        var gb=fixture("BLUE","SOLID","GB",null);var india=fixture("BLUE","SOLID","IN",gb.retailer());var other=fixture("WHITE","SOLID","GB",null);
        var s=source(gb.url(),true);
        var restricted=create(s,MatchMode.COORDINATED,true,UUID.randomUUID());assertThat(restricted.path("results").size()).isEqualTo(1);
        assertThat(restricted.path("results").get(0).path("offer").path("offerId").asString()).isEqualTo(gb.offer().toString());
        var all=create(s,MatchMode.COORDINATED,false,UUID.randomUUID());assertThat(all.path("results").size()).isEqualTo(2);assertThat(all.toString()).doesNotContain(india.offer().toString());
        var retrieval=new CompatibilityRequest(s.revision(),new com.coupleoutfit.market.domain.MarketContext("GB","GBP","en-GB"),ProductTarget.MENSWEAR,ProductCategory.DRESS,1,other.market());
        // A restricted limit of one must select the other market rather than filter an already-limited set.
        var revision=analyses.findBySource(s.id()).orElseThrow().currentRevision();
        assertThat(retrieve.retrieveSnapshot(retrieval,revision)).extracting(CompatibilityCandidate::productOfferId).containsExactly(other.offer());
    }
    @Autowired RetrieveCompatibilityCandidates retrieve;
    @Test void unavailableRetailerOwnershipProblemDetailsAndWriteGuard() throws Exception {
        var s=source(null,true);var stranger=source(null,false);
        mvc.perform(post("/api/v1/match-sessions").cookie(s.cookie()).header("X-Source-Request","1").header("Idempotency-Key",UUID.randomUUID()).header("X-Request-Id","match-error")
                .contentType("application/json").content(json.writeValueAsString(command(s,MatchMode.EXACT,true)))).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("SAME_RETAILER_NOT_AVAILABLE")).andExpect(jsonPath("$.traceId").value("match-error"));
        var body=create(s,null,false,UUID.randomUUID());String id=body.path("id").asString();
        assertThat(body.path("configuration").path("matchMode").asString()).isEqualTo("COORDINATED");
        assertThat(body.path("status").asString()).isEqualTo("COMPLETED");assertThat(body.path("results").size()).isZero();
        for(String target:List.of(id,UUID.randomUUID().toString()))mvc.perform(get("/api/v1/match-sessions/"+target).cookie(stranger.cookie())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MATCH_SESSION_NOT_FOUND"));
        mvc.perform(get("/api/v1/match-sessions/"+id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/match-sessions").cookie(s.cookie()).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsString(command(s,null,false)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/match-sessions").cookie(stranger.cookie()).header("X-Source-Request","1").header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsString(command(s,null,false)))).andExpect(status().isNotFound());
        assertThatThrownBy(()->matches.create(command(stranger,null,false),stranger.owner(),UUID.randomUUID())).hasMessage("MATCH_SOURCE_NOT_READY");
        // Old path cookie upgrade preserves the exact token, hence ownership.
        mvc.perform(get("/api/v1/source-items/"+s.id()).cookie(s.cookie())).andExpect(status().isOk()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString(s.cookie().getValue()+"; Path=/api/v1;")));
    }
    @Test void invalidPublicConfigurationAndUnsupportedKnownRetailer() throws Exception {
        var s=source(null,true);String valid=json.writeValueAsString(command(s,null,false));
        for(String invalid:List.of(valid.substring(0,valid.length()-1)+",\"countryCode\":\"IN\"}",valid.replace("COORDINATED","COMPLETE_MY_LOOK"),valid.replace("MENSWEAR","INVALID"))) {
            mvc.perform(post("/api/v1/match-sessions").cookie(s.cookie()).header("X-Source-Request","1").header("Idempotency-Key",UUID.randomUUID())
                    .contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/match-sessions").cookie(s.cookie()).header("X-Source-Request","1").contentType("application/json").content(valid)).andExpect(status().isBadRequest());
        var known=fixture("BLUE","SOLID","GB",null);
        jdbc.update("UPDATE source_item SET source_type='PRODUCT_URL' WHERE id=?",s.id());
        jdbc.update("INSERT INTO source_url VALUES (?,?,?,'UNSUPPORTED','CATALOGUE',?,NULL,NULL,'SOURCE_URL_UNSUPPORTED',now())",s.id(),known.url(),known.url(),known.market());
        assertThatThrownBy(()->matches.create(command(s,null,true),s.owner(),UUID.randomUUID())).hasMessage("SAME_RETAILER_NOT_AVAILABLE");
    }
    @Test void publicThresholdKeepsSixtyAndHidesFiftyNine() throws Exception {
        var below=fixture("BLUE","SOLID","GB",null);var boundary=fixture("BLUE","SOLID","GB",null);
        for(var f:List.of(below,boundary)) {
            jdbc.update("UPDATE product SET attributes='{\"primaryColors\":[\"BLUE\"]}' WHERE id=?",f.product().id());
            double cosine=f==below?.44:.46;
            var values=new ArrayList<String>(Collections.nCopies(1536,"0"));values.set(0,Double.toString(cosine));values.set(1,Double.toString(Math.sqrt(1-cosine*cosine)));
            jdbc.update("UPDATE product_embedding SET embedding=CAST(? AS vector) WHERE product_id=?","["+String.join(",",values)+"]",f.product().id());
        }
        var s=source(null,true);var response=create(s,MatchMode.EXACT,false,UUID.randomUUID());
        assertThat(response.path("results").size()).isEqualTo(1);
        assertThat(response.path("results").get(0).path("coupleMatchScore").asInt()).isEqualTo(60);
        assertThat(response.path("results").get(0).path("offer").path("offerId").asString()).isEqualTo(boundary.offer().toString());
    }
    @Test void currentRevisionValidationAndFrozenRetry() throws Exception {
        var s=source(null,true);var key=UUID.randomUUID();var original=matches.create(command(s,null,false),s.owner(),key);
        var corrected=analyses.correct(s.id(),s.revision(),AnalysisFixtures.attributes(),Instant.now());
        assertThatThrownBy(()->matches.create(command(s,null,false),s.owner(),UUID.randomUUID())).hasMessage("MATCH_REVISION_NOT_CURRENT");
        assertThat(matches.create(command(s,null,false),s.owner(),key)).isEqualTo(original);
        var newCommand=new CreateMatchCommand(s.id(),corrected.currentRevision().id(),ProductTarget.MENSWEAR,MatchMode.EXACT,false);
        assertThat(matches.create(newCommand,s.owner(),UUID.randomUUID()).analysisRevisionId()).isEqualTo(corrected.currentRevision().id());
        assertThatThrownBy(()->matches.create(newCommand,s.owner(),key)).hasMessage("MATCH_IDEMPOTENCY_CONFLICT");
        assertThatThrownBy(()->jdbc.update("UPDATE match_session SET match_mode='EXACT' WHERE id=?",original.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var pending=sessions.createOrFind(newCommand,s.owner(),"b".repeat(64),50,12,60,Instant.now());
        analyses.correct(s.id(),corrected.currentRevision().id(),attributes(),Instant.now());
        assertThat(sessions.revision(pending.id()).id()).isEqualTo(corrected.currentRevision().id());
        assertThat(retrieve.retrieveSnapshot(new CompatibilityRequest(pending.analysisRevisionId(),pending.market(),pending.partnerTarget(),pending.sourceAttributes().category(),50),sessions.revision(pending.id()))).isEmpty();
    }
    @Test void concurrentIdempotentExecutionAndRetrievalFailure() throws Exception {
        var s=source(null,true);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var key=UUID.randomUUID();
        reset(embeddings);when(embeddings.embed(any())).thenAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return vector();});
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->matches.create(command(s,null,false),s.owner(),key));
            try {assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();var second=pool.submit(()->matches.create(command(s,null,false),s.owner(),key));
                assertThat(second.get(5,TimeUnit.SECONDS).status()).isEqualTo(MatchStatus.MATCHING);
            }finally {release.countDown();}
            assertThat(first.get(10,TimeUnit.SECONDS).status()).isEqualTo(MatchStatus.COMPLETED);
        }
        verify(embeddings,times(1)).embed(any());assertThat(jdbc.queryForObject("SELECT count(*) FROM match_session WHERE source_item_id=?",Integer.class,s.id())).isEqualTo(1);
        var failedSource=source(null,true);reset(embeddings);when(embeddings.embed(any())).thenThrow(new EmbeddingException("EMBEDDING_INVALID_RESPONSE"));
        var failed=matches.create(command(failedSource,null,false),failedSource.owner(),UUID.randomUUID());
        assertThat(failed.status()).isEqualTo(MatchStatus.FAILED);assertThat(failed.failureCode()).isEqualTo("MATCH_RETRIEVAL_FAILED");assertThat(sessions.results(failed.id())).isEmpty();
    }
    @Test void migrationUpgradeConstraintsAndAtomicCompletion() throws Exception {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6","7");
        var config=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).defaultSchema("matching_upgrade").schemas("matching_upgrade");
        config.target("6").load().migrate();var checksums=jdbc.queryForList("SELECT checksum FROM matching_upgrade.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",Integer.class);
        assertThat(checksums).containsExactly(-929483003,-362163080,-360589483,-599056603,85417613,-976671557);
        config.target("7").load().migrate();config.load().validate();
        assertThat(jdbc.queryForList("SELECT checksum FROM matching_upgrade.flyway_schema_history WHERE version<>'7' AND version IS NOT NULL ORDER BY installed_rank",Integer.class)).isEqualTo(checksums);
        var f=fixture("BLUE","SOLID","GB",null);var s=source(null,true);
        var pending=sessions.createOrFind(command(s,MatchMode.EXACT,false),s.owner(),"d".repeat(64),50,12,60,Instant.now());sessions.claim(pending.id(),Instant.now());
        var result=new MatchResult(UUID.randomUUID(),pending.id(),f.offer(),1,new CoupleMatchScore(100),ScoreBand.EXCELLENT,new CompatibilityScoreBreakdown(100,100,100,100,100,100,100),new MatchExplanation(List.of("Similar styling.")),RankingAlgorithm.VERSION,Instant.now());
        var duplicate=new MatchResult(UUID.randomUUID(),pending.id(),f.offer(),2,result.coupleMatchScore(),result.scoreBand(),result.breakdown(),result.explanation(),result.algorithmVersion(),result.createdAt());
        assertThatThrownBy(()->sessions.complete(pending.id(),List.of(result,duplicate),Instant.now())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(sessions.results(pending.id())).isEmpty();assertThat(sessions.findOwned(pending.id(),s.owner()).status()).isEqualTo(MatchStatus.MATCHING);
        var other=fixture("BLUE","SOLID","GB",null);
        var duplicateRank=new MatchResult(UUID.randomUUID(),pending.id(),other.offer(),1,result.coupleMatchScore(),result.scoreBand(),result.breakdown(),result.explanation(),result.algorithmVersion(),result.createdAt());
        assertThatThrownBy(()->sessions.complete(pending.id(),List.of(result,duplicateRank),Instant.now())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(sessions.results(pending.id())).isEmpty();
        var foreignKeys=jdbc.queryForList("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='match_session'::regclass AND contype='f'",String.class);
        assertThat(foreignKeys).anyMatch(k->k.contains("analysis_revision_id") && k.contains("analysis_revision(id)"));
        assertThat(foreignKeys).anyMatch(k->k.contains("source_retailer_market_id") && k.contains("retailer_market(id)"));
        sessions.complete(pending.id(),List.of(result),Instant.now());
        assertThatThrownBy(()->jdbc.update("UPDATE match_result SET couple_match_score=101 WHERE id=?",result.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE match_session SET analysis_revision_id=? WHERE id=?",UUID.randomUUID(),pending.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE match_session SET source_retailer_market_id=? WHERE id=?",UUID.randomUUID(),pending.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(sessions.claim(pending.id(),Instant.now())).isFalse();
    }
}
