package com.coupleoutfit.feed;

import com.coupleoutfit.affiliate.application.*;
import com.coupleoutfit.affiliate.domain.*;
import com.coupleoutfit.feed.application.*;
import com.coupleoutfit.feed.domain.*;
import com.coupleoutfit.catalogue.application.CatalogueRepository;
import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.compatibility.application.*;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.analysis.application.ClothingAnalysisRepository;
import com.coupleoutfit.analysis.AnalysisFixtures;
import com.coupleoutfit.matching.domain.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test") @Testcontainers
class FeedIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres"));
    static final Path ROOT=temporaryRoot();
    private static Path temporaryRoot(){try{return Files.createTempDirectory("coupleoutfit-feed-test-");}catch(IOException e){throw new UncheckedIOException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("coupleoutfit.feed.local-root",ROOT::toString);registry.add("coupleoutfit.feed.batch-size",()->2);}
    @Autowired JdbcTemplate jdbc;@Autowired AffiliateConfigurationRepository definitions;@Autowired FeedImportService imports;@Autowired FeedRunRepository runs;
    @Autowired CatalogueRepository catalogue;@Autowired EnsureProductEmbedding prepare;@Autowired RetrieveCompatibilityCandidates retrieval;
    @Autowired ClothingAnalysisRepository analyses;@Autowired EmbeddingModelMetadata model;@Autowired AffiliateFeedReader reader;@Autowired FeedContentProvider content;
    @MockitoBean EmbeddingProvider embeddingProvider;
    record Fixture(UUID retailer,UUID market,AffiliateProgram program,AffiliateFeed feed) {}
    @BeforeEach void fakeEmbedding() {
        when(embeddingProvider.embed(any())).thenAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();var v=new ArrayList<Float>(Collections.nCopies(1536,0f));v.set(0,1f);return new EmbeddingProvider.Result(new EmbeddingVector(v),model,null);});
    }
    @AfterEach void cleanup()throws Exception {
        jdbc.update("DELETE FROM product_embedding");jdbc.update("DELETE FROM product_offer");jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM affiliate_feed_run");jdbc.update("DELETE FROM affiliate_feed");jdbc.update("DELETE FROM affiliate_program");
        jdbc.update("DELETE FROM retailer_market");jdbc.update("DELETE FROM retailer");
        try(var files=Files.list(ROOT)){for(var path:files.toList())Files.delete(path);}
    }
    @AfterAll static void removeRoot()throws IOException{Files.delete(ROOT);}
    private String csv(String name)throws IOException {try(var input=getClass().getResourceAsStream("/feeds/"+name)){return new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);}}
    private Fixture fixture(String country,String file)throws IOException {
        String currency=country.equals("GB")?"GBP":"INR",locale=country.equals("GB")?"en-GB":"en-IN";
        if(country.equals("IN")) {
            jdbc.update("INSERT INTO country(code,name,active) VALUES ('IN','India',true) ON CONFLICT DO NOTHING");
            jdbc.update("INSERT INTO currency(code,minor_unit_digits,active) VALUES ('INR',2,true) ON CONFLICT DO NOTHING");
            jdbc.update("INSERT INTO locale(code,country_code,default_currency_code,active) VALUES ('en-IN','IN','INR',true) ON CONFLICT DO NOTHING");
        }
        UUID retailer=UUID.randomUUID(),market=UUID.randomUUID();
        jdbc.update("INSERT INTO retailer VALUES (?,?,'Feed fixture','ACTIVE',now(),now())",retailer,"feed-"+retailer);
        jdbc.update("INSERT INTO retailer_market VALUES (?,?,?,?,?,'example.com','ACTIVE',true,true,now(),now())",market,retailer,country,currency,locale);
        var program=FeedFixtures.program(market,AffiliateProgramStatus.ACTIVE);var feed=FeedFixtures.feed(program,file,FeedSchema.genericCsv());
        definitions.createProgram(program);definitions.createFeed(feed);Files.writeString(ROOT.resolve(file),csv("valid.csv").replace("GBP",currency));
        return new Fixture(retailer,market,program,feed);
    }
    private ProductOffer offer(Fixture f,String id){return catalogue.findOfferByMarketAndRetailerProductId(f.market(),id).orElseThrow();}
    private UUID source() {
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO source_item VALUES (?,?,'IMAGE_UPLOAD','READY_FOR_ANALYSIS','GB','GBP','en-GB',now(),now())",id,"a".repeat(64));
        var claim=analyses.claim(id,Instant.now());analyses.complete(claim.snapshot().analysis().id(),AnalysisFixtures.result(),Instant.now());return id;
    }
    @Test void idempotentImportPriceUpdateMissingPriceAndEmbeddingSeparation()throws Exception {
        var f=fixture("GB","fixture.csv");var first=imports.run(f.feed().id());
        assertThat(first.status()).isEqualTo(FeedRunStatus.COMPLETED);assertThat(first.counters()).isEqualTo(new FeedCounters(3,3,0,3,0,3,0,0));verifyNoInteractions(embeddingProvider);
        var a=offer(f,"A");var p=catalogue.findProduct(a.productId()).orElseThrow();assertThat(a.price().amountMinor()).isEqualTo(4999);assertThat(offer(f,"C").price()).isNull();
        assertThat(a.retailerMarketId()).isEqualTo(f.market());assertThat(a.lastSeenAt()).isEqualTo(first.startedAt());
        var second=imports.run(f.feed().id());assertThat(second.counters()).isEqualTo(new FeedCounters(3,3,0,0,0,0,3,0));
        assertThat(offer(f,"A").id()).isEqualTo(a.id());assertThat(offer(f,"A").createdAt()).isEqualTo(a.createdAt());assertThat(catalogue.findProduct(p.id()).orElseThrow()).isEqualTo(p);
        var embedding=prepare.ensure(p.id());clearInvocations(embeddingProvider);
        Files.writeString(ROOT.resolve("fixture.csv"),csv("valid.csv").replace("49.99","44.99").replace("in stock","available").replace("network.example/click","network.example/new"));
        var changed=imports.run(f.feed().id());assertThat(changed.counters().productsUpdated()).isZero();assertThat(offer(f,"A").price().amountMinor()).isEqualTo(4499);
        assertThat(offer(f,"A").id()).isEqualTo(a.id());assertThat(catalogue.findProduct(p.id()).orElseThrow()).isEqualTo(p);
        assertThat(prepare.ensure(p.id())).isEqualTo(embedding);verifyNoInteractions(embeddingProvider);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product",Integer.class)).isEqualTo(3);assertThat(jdbc.queryForObject("SELECT count(*) FROM product_offer",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT last_affiliate_feed_id FROM product_offer WHERE id=?",UUID.class,a.id())).isEqualTo(f.feed().id());
    }
    @Test void styleUpdatesInvalidateVectorsAndImportedOffersRespectRetrievalMarketAndTarget()throws Exception {
        var gb=fixture("GB","gb.csv");var india=fixture("IN","india.csv");imports.run(gb.feed().id());imports.run(india.feed().id());
        for(UUID product:jdbc.queryForList("SELECT id FROM product",UUID.class))prepare.ensure(product);
        var source=source();var found=retrieval.retrieve(source,"a".repeat(64),ProductTarget.MENSWEAR,50);
        assertThat(found).extracting(CompatibilityCandidate::productOfferId).containsExactlyInAnyOrder(offer(gb,"A").id(),offer(gb,"C").id());
        assertThat(new CandidateRanker().rank(found,MatchMode.COORDINATED,60,12)).isNotEmpty();
        var a=offer(gb,"A");var before=catalogue.findProduct(a.productId()).orElseThrow();var descriptor=new EmbeddingDescriptorFactory().product(before);
        Files.writeString(ROOT.resolve("gb.csv"),csv("valid.csv").replace(",blue,",",green,"));
        assertThat(imports.run(gb.feed().id()).counters().productsUpdated()).isEqualTo(1);
        var after=catalogue.findProduct(a.productId()).orElseThrow();assertThat(after.createdAt()).isEqualTo(before.createdAt());assertThat(after.updatedAt()).isAfter(before.updatedAt());
        assertThat(new EmbeddingDescriptorFactory().product(after).hash()).isNotEqualTo(descriptor.hash());
        assertThat(retrieval.retrieve(source,"a".repeat(64),ProductTarget.MENSWEAR,50)).extracting(CompatibilityCandidate::productOfferId).containsExactly(offer(gb,"C").id());
        prepare.ensure(a.productId());assertThat(retrieval.retrieve(source,"a".repeat(64),ProductTarget.MENSWEAR,50)).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_embedding WHERE product_id=?",Integer.class,a.productId())).isEqualTo(2);
    }
    @Test void rowErrorsDuplicatesAndWrongCurrencyRetainValidRows()throws Exception {
        var f=fixture("GB","fixture.csv");Files.writeString(ROOT.resolve("fixture.csv"),csv("mixed.csv"));var run=imports.run(f.feed().id());
        assertThat(run.status()).isEqualTo(FeedRunStatus.COMPLETED_WITH_ERRORS);assertThat(run.counters()).isEqualTo(new FeedCounters(9,5,4,4,0,4,1,0));
        assertThat(run.rejectionCounts()).containsEntry("FEED_CURRENCY_MISMATCH",1L).containsEntry("FEED_CATEGORY_UNSUPPORTED",1L).containsEntry("FEED_PRICE_INVALID",1L).containsEntry("FEED_TARGET_UNSUPPORTED",1L);
        assertThat(offer(f,"A").price().amountMinor()).isEqualTo(4499);assertThat(offer(f,"D").availabilityStatus()).isEqualTo(AvailabilityStatus.UNKNOWN);
        assertThat(offer(f,"B").availabilityStatus()).isEqualTo(AvailabilityStatus.OUT_OF_STOCK);assertThat(offer(f,"C").price()).isNull();
        assertThat(definitions.findFeed(f.feed().id()).orElseThrow().lastSuccessfulRunAt()).isNull();
    }
    @Test void successfulAbsenceMarksUnknownButNeverDeletesAndEmptyOrRejectedSnapshotsDoNotReconcile()throws Exception {
        var f=fixture("GB","fixture.csv");String all=csv("valid.csv").replace("unknown,shirt","in stock,shirt");Files.writeString(ROOT.resolve("fixture.csv"),all);imports.run(f.feed().id());var missing=offer(f,"C");
        String reduced=all.lines().filter(line->!line.startsWith("C,")).collect(java.util.stream.Collectors.joining("\n"))+"\n";
        Files.writeString(ROOT.resolve("fixture.csv"),reduced);var run=imports.run(f.feed().id());assertThat(run.counters().offersMarkedStale()).isEqualTo(1);
        assertThat(offer(f,"C").availabilityStatus()).isEqualTo(AvailabilityStatus.UNKNOWN);assertThat(offer(f,"C").active()).isTrue();assertThat(offer(f,"C").id()).isEqualTo(missing.id());
        assertThat(offer(f,"C").lastSeenAt()).isEqualTo(missing.lastSeenAt());assertThat(offer(f,"B").availabilityStatus()).isEqualTo(AvailabilityStatus.OUT_OF_STOCK);
        Files.writeString(ROOT.resolve("fixture.csv"),all);imports.run(f.feed().id());assertThat(offer(f,"C").availabilityStatus()).isEqualTo(AvailabilityStatus.IN_STOCK);
        Files.writeString(ROOT.resolve("fixture.csv"),all.lines().findFirst().orElseThrow()+"\n");assertThat(imports.run(f.feed().id()).counters().offersMarkedStale()).isZero();
        assertThat(offer(f,"C").availabilityStatus()).isEqualTo(AvailabilityStatus.IN_STOCK);
        Files.writeString(ROOT.resolve("fixture.csv"),reduced.replace("49.99","broken"));assertThat(imports.run(f.feed().id()).status()).isEqualTo(FeedRunStatus.COMPLETED_WITH_ERRORS);
        assertThat(offer(f,"C").availabilityStatus()).isEqualTo(AvailabilityStatus.IN_STOCK);
    }
    @Test void readFailureAfterCommittedBatchDoesNotReconcileAndCountersSurvive()throws Exception {
        var f=fixture("GB","fixture.csv");imports.run(f.feed().id());var a=offer(f,"A");
        String headerAndRow=csv("valid.csv").lines().limit(2).collect(java.util.stream.Collectors.joining("\n"))+"\n";
        FeedContentProvider broken=feed->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new SequenceInputStream(new ByteArrayInputStream(headerAndRow.getBytes(StandardCharsets.UTF_8)),new InputStream(){@Override public int read()throws IOException{throw new IOException("private download details");}});};
        var service=new FeedImportService(runs,broken,reader,new FeedItemNormalizer(),Clock.systemUTC(),1,100);
        var failed=service.run(f.feed().id());assertThat(failed.status()).isEqualTo(FeedRunStatus.FAILED);assertThat(failed.counters().offersMarkedStale()).isZero();assertThat(failed.counters().itemsAccepted()).isEqualTo(1);
        assertThat(failed.failureCode()).startsWith("FEED_").doesNotContain("private");assertThat(offer(f,"A").id()).isEqualTo(a.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_offer",Integer.class)).isEqualTo(3);
        assertThat(definitions.findFeed(f.feed().id()).orElseThrow().lastSuccessfulRunAt()).isBefore(failed.startedAt());
    }
    @Test void activeRunConcurrencyAndDisabledDefinitions()throws Exception {
        var f=fixture("GB","fixture.csv");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        FeedContentProvider blocked=feed->{entered.countDown();try{assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return Files.newInputStream(ROOT.resolve("fixture.csv"));}catch(Exception e){throw new FeedException("FEED_DOWNLOAD_FAILED");}};
        var service=new FeedImportService(runs,blocked,reader,new FeedItemNormalizer(),Clock.systemUTC(),2,100);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->service.run(f.feed().id()));
            try{assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();assertThatThrownBy(()->imports.run(f.feed().id())).hasMessage("FEED_RUN_ALREADY_ACTIVE");}
            finally{release.countDown();}
            assertThat(first.get(10,TimeUnit.SECONDS).status()).isEqualTo(FeedRunStatus.COMPLETED);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM affiliate_feed_run",Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE affiliate_feed SET status='PAUSED' WHERE id=?",f.feed().id());assertThatThrownBy(()->imports.run(f.feed().id())).hasMessage("FEED_DISABLED");
        jdbc.update("UPDATE affiliate_feed SET status='ACTIVE' WHERE id=?",f.feed().id());jdbc.update("UPDATE affiliate_program SET status='DISABLED' WHERE id=?",f.program().id());
        assertThatThrownBy(()->imports.run(f.feed().id())).hasMessage("FEED_DISABLED");assertThatThrownBy(()->imports.run(UUID.randomUUID())).hasMessage("FEED_NOT_FOUND");
    }
    @Test void competingFeedsCannotStealOfferProvenanceOrReconcileEachOther()throws Exception {
        var f=fixture("GB","fixture.csv");imports.run(f.feed().id());var other=FeedFixtures.feed(f.program(),"other.csv",FeedSchema.genericCsv());definitions.createFeed(other);Files.writeString(ROOT.resolve("other.csv"),csv("valid.csv"));
        var conflicting=imports.run(other.id());assertThat(conflicting.status()).isEqualTo(FeedRunStatus.COMPLETED_WITH_ERRORS);assertThat(conflicting.counters().itemsAccepted()).isZero();
        assertThat(conflicting.rejectionCounts()).containsEntry("FEED_OFFER_OWNED_BY_ANOTHER_FEED",3L);
        assertThat(jdbc.queryForList("SELECT DISTINCT last_affiliate_feed_id FROM product_offer",UUID.class)).containsExactly(f.feed().id());
        var strict=new FeedImportService(runs,content,reader,new FeedItemNormalizer(),Clock.systemUTC(),2,0);
        var failed=strict.run(other.id());assertThat(failed.status()).isEqualTo(FeedRunStatus.FAILED);
        assertThat(failed.failureCode()).isEqualTo("FEED_TOO_MANY_REJECTIONS");assertThat(failed.counters().itemsRead()).isEqualTo(2);
    }
    @Test void migrationUpgradeConstraintsAndRejectionThreshold()throws Exception {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6","7","8","9");
        var config=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).defaultSchema("feed_upgrade").schemas("feed_upgrade");config.target("7").load().migrate();
        var old=jdbc.queryForList("SELECT checksum FROM feed_upgrade.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",Integer.class);
        assertThat(old).containsExactly(-929483003,-362163080,-360589483,-599056603,85417613,-976671557,-1417818194);
        config.target("8").load().migrate();config.load().validate();assertThat(jdbc.queryForList("SELECT checksum FROM feed_upgrade.flyway_schema_history WHERE version IS NOT NULL AND version<>'8' ORDER BY installed_rank",Integer.class)).isEqualTo(old);
        var f=fixture("GB","fixture.csv");var active=runs.start(f.feed().id(),Instant.now());
        assertThatThrownBy(()->jdbc.update("INSERT INTO affiliate_feed_run(id,affiliate_feed_id,status,started_at,created_at) VALUES (?,?,'STARTED',now(),now())",UUID.randomUUID(),f.feed().id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE affiliate_feed_run SET items_read=-1 WHERE id=?",active.run().id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE affiliate_feed SET retailer_market_id=? WHERE id=?",UUID.randomUUID(),f.feed().id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        runs.fail(active.run().id(),"FEED_RUN_FAILED",Instant.now());
        Files.writeString(ROOT.resolve("fixture.csv"),csv("mixed.csv"));var strict=new FeedImportService(runs,feed->{try{return Files.newInputStream(ROOT.resolve("fixture.csv"));}catch(IOException e){throw new UncheckedIOException(e);}},reader,new FeedItemNormalizer(),Clock.systemUTC(),2,0);
        var failed=strict.run(f.feed().id());assertThat(failed.status()).isEqualTo(FeedRunStatus.FAILED);assertThat(failed.failureCode()).isEqualTo("FEED_TOO_MANY_REJECTIONS");assertThat(failed.counters().offersMarkedStale()).isZero();
        var offer=offer(f,"A");assertThatThrownBy(()->jdbc.update("UPDATE product_offer SET last_affiliate_feed_id=? WHERE id=?",UUID.randomUUID(),offer.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
