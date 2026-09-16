package com.coupleoutfit.compatibility;

import com.coupleoutfit.compatibility.application.*;
import com.coupleoutfit.compatibility.domain.*;
import com.coupleoutfit.catalogue.application.*;
import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.analysis.application.*;
import com.coupleoutfit.analysis.domain.*;
import com.coupleoutfit.analysis.AnalysisFixtures;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="coupleoutfit.ai.embeddings.retry-backoff=PT0S") @ActiveProfiles("test") @Testcontainers
class CompatibilityIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres"));
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogueWriter catalogue;
    @Autowired EnsureProductEmbedding products;
    @Autowired EnsureAnalysisEmbedding analyses;
    @Autowired RetrieveCompatibilityCandidates retrieve;
    @Autowired ClothingAnalysisRepository analysisRepository;
    @Autowired EmbeddingRepository embeddings;
    @Autowired EmbeddingModelMetadata model;
    @Autowired CandidateRetrievalPort candidates;
    @MockitoBean EmbeddingProvider provider;
    static final String OWNER="a".repeat(64);
    record Fixture(UUID retailer,UUID market,Product product,UUID offer) {}
    @BeforeEach void fake() {
        when(provider.embed(any())).thenAnswer(call -> {assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new DeterministicEmbeddingProvider(model).embed(call.getArgument(0));});
    }
    @AfterEach void cleanCatalogue() {
        jdbc.update("DELETE FROM product_embedding");jdbc.update("DELETE FROM product_offer");jdbc.update("DELETE FROM product");jdbc.update("DELETE FROM retailer_market");jdbc.update("DELETE FROM retailer");
    }
    private Fixture fixture(String color,ProductTarget target,ProductCategory category,String country) {
        if(country.equals("IN")) {
            jdbc.update("INSERT INTO country(code,name,active) VALUES ('IN','India',true) ON CONFLICT DO NOTHING");
            jdbc.update("INSERT INTO currency(code,minor_unit_digits,active) VALUES ('INR',2,true) ON CONFLICT DO NOTHING");
            jdbc.update("INSERT INTO locale(code,country_code,default_currency_code,active) VALUES ('en-IN','IN','INR',true) ON CONFLICT DO NOTHING");
        }
        var retailer=UUID.randomUUID();var market=UUID.randomUUID();var now=Instant.now();
        jdbc.update("INSERT INTO retailer VALUES (?,?,'Fixture','ACTIVE',now(),now())",retailer,"fixture-"+retailer);
        String currency=country.equals("GB")?"GBP":"INR",locale=country.equals("GB")?"en-GB":"en-IN";
        jdbc.update("INSERT INTO retailer_market VALUES (?,?,?,?,?,'example.com','ACTIVE',true,true,now(),now())",market,retailer,country,currency,locale);
        var p=catalogue.createProduct(new Product(UUID.randomUUID(),retailer,"Fixture garment",null,category,null,target,new ProductAttributes(Map.of("primaryColors",List.of(color),"pattern",List.of("SOLID"),"formality",List.of("SMART"))),"1",ProductStatus.ACTIVE,now,now));
        var offer=catalogue.upsertOffer(new ProductOffer(UUID.randomUUID(),p.id(),market,"fixture-1","Fixture","https://example.com/p","https://private.example/affiliate",new Money(4999,currency),AvailabilityStatus.IN_STOCK,null,true,now,now,now,now));
        return new Fixture(retailer,market,p,offer.id());
    }
    private Fixture fixture(String color){return fixture(color,ProductTarget.MENSWEAR,ProductCategory.SHIRT,"GB");}
    private UUID source() {
        var id=UUID.randomUUID();jdbc.update("INSERT INTO source_item VALUES (?,?,'IMAGE_UPLOAD','READY_FOR_ANALYSIS','GB','GBP','en-GB',now(),now())",id,OWNER);
        var claim=analysisRepository.claim(id,Instant.now());
        var a=AnalysisFixtures.attributes();var attrs=new ClothingAttributes(a.category(),a.subcategory(),List.of("BLUE"),a.secondaryColors(),a.pattern(),a.materialAppearance(),a.fit(),a.formality(),a.seasonality(),a.styleTags(),a.details());
        analysisRepository.complete(claim.snapshot().analysis().id(),new ClothingAnalysisProviderResult(attrs,AnalysisFixtures.result().confidence(),"OPENAI","test-vision",null,"clothing-analysis-v1"),Instant.now());
        return id;
    }
    @Test void eligibilityMarketTargetAvailabilityCategoryAndVectorOrdering() {
        var source=source();var near=fixture("NAVY");var far=fixture("RED");var best=fixture("BLUE");
        var unknown=fixture("NAVY");var otherMarket=fixture("BLUE",ProductTarget.MENSWEAR,ProductCategory.SHIRT,"IN");
        var otherTarget=fixture("BLUE",ProductTarget.WOMENSWEAR,ProductCategory.DRESS,"GB");var accessory=fixture("BLUE",ProductTarget.MENSWEAR,ProductCategory.ACCESSORY,"GB");
        fixture("BLUE"); // An offer without a prepared embedding must be excluded.
        var stale=fixture("BLUE");var inactive=fixture("BLUE");var catalogueDisabled=fixture("BLUE");var matchingDisabled=fixture("BLUE");var out=fixture("BLUE");var noPrice=fixture("NAVY");var inactiveProduct=fixture("BLUE");var inactiveOffer=fixture("BLUE");var inactiveRetailer=fixture("BLUE");
        products.prepareBatch(List.of(near,far,best,unknown,otherMarket,otherTarget,accessory,stale,inactive,catalogueDisabled,matchingDisabled,out,noPrice,inactiveProduct,inactiveOffer,inactiveRetailer).stream().map(f->f.product().id()).toList());
        jdbc.update("UPDATE product_offer SET availability_status='UNKNOWN' WHERE id=?",unknown.offer());
        jdbc.update("UPDATE retailer_market SET status='INACTIVE' WHERE id=?",inactive.market());
        jdbc.update("UPDATE retailer_market SET catalogue_enabled=false WHERE id=?",catalogueDisabled.market());
        jdbc.update("UPDATE retailer_market SET matching_enabled=false WHERE id=?",matchingDisabled.market());
        jdbc.update("UPDATE product_offer SET availability_status='OUT_OF_STOCK' WHERE id=?",out.offer());
        jdbc.update("UPDATE product_offer SET amount_minor=NULL,currency_code=NULL WHERE id=?",noPrice.offer());
        jdbc.update("UPDATE product SET status='INACTIVE' WHERE id=?",inactiveProduct.product().id());
        jdbc.update("UPDATE product_offer SET active=false WHERE id=?",inactiveOffer.offer());
        jdbc.update("UPDATE retailer SET status='INACTIVE' WHERE id=?",inactiveRetailer.retailer());
        jdbc.update("UPDATE product SET updated_at=updated_at+interval '1 second' WHERE id=?",stale.product().id());
        var result=retrieve.retrieve(source,OWNER,ProductTarget.MENSWEAR,50);
        assertThat(result).extracting(CompatibilityCandidate::productId).containsExactlyInAnyOrder(best.product().id(),near.product().id(),far.product().id(),unknown.product().id(),noPrice.product().id());
        assertThat(result.getFirst().productId()).isEqualTo(best.product().id());assertThat(result.getLast().productId()).isEqualTo(far.product().id());
        assertThat(result).extracting(CompatibilityCandidate::semanticSimilarity).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(result).filteredOn(c->c.productId().equals(unknown.product().id())).allMatch(c->c.availability()==AvailabilityStatus.UNKNOWN);
        assertThat(result.toString()).doesNotContain("affiliate","private.example");
        assertThat(retrieve.retrieve(source,OWNER,ProductTarget.MENSWEAR,1)).hasSize(1);
        assertThat(retrieve.retrieve(source,OWNER,ProductTarget.WOMENSWEAR,50)).extracting(CompatibilityCandidate::productId).containsExactly(otherTarget.product().id());
        assertThat(result).allMatch(c->c.features().sourceColors().contains("BLUE"));
    }
    @Test void missingPricePreservesAvailabilityMarketAndCurrencyEligibility() {
        var source=source();var inStock=fixture("BLUE");var unknown=fixture("NAVY");var out=fixture("BLUE");
        var wrongMarket=fixture("BLUE",ProductTarget.MENSWEAR,ProductCategory.SHIRT,"IN");
        var priced=fixture("RED");
        products.prepareBatch(List.of(inStock,unknown,out,wrongMarket,priced).stream().map(f->f.product().id()).toList());
        for(var f:List.of(inStock,unknown,out,wrongMarket))
            jdbc.update("UPDATE product_offer SET amount_minor=NULL,currency_code=NULL WHERE id=?",f.offer());
        jdbc.update("UPDATE product_offer SET availability_status='UNKNOWN' WHERE id=?",unknown.offer());
        jdbc.update("UPDATE product_offer SET availability_status='OUT_OF_STOCK' WHERE id=?",out.offer());
        // Existing schema rejects a priced GB offer with a different or absent currency.
        assertThatThrownBy(()->jdbc.update("UPDATE product_offer SET currency_code='INR' WHERE id=?",priced.offer()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE product_offer SET currency_code=NULL WHERE id=?",priced.offer()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        var result=retrieve.retrieve(source,OWNER,ProductTarget.MENSWEAR,50);
        assertThat(result).extracting(CompatibilityCandidate::productOfferId).containsExactlyInAnyOrder(inStock.offer(),unknown.offer(),priced.offer());
        assertThat(result).filteredOn(c->c.productOfferId().equals(unknown.offer())).singleElement()
                .satisfies(c->assertThat(c.availability()).isEqualTo(AvailabilityStatus.UNKNOWN));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_offer WHERE id IN (?,?) AND amount_minor IS NULL AND currency_code IS NULL",Integer.class,inStock.offer(),unknown.offer())).isEqualTo(2);
    }
    @Test void emptyCatalogueCurrentCorrectionsAndOwnership() {
        var source=source();assertThat(retrieve.retrieve(source,OWNER,ProductTarget.MENSWEAR,50)).isEmpty();
        var first=analysisRepository.findBySource(source).orElseThrow();
        retrieve.retrieve(source,OWNER,ProductTarget.MENSWEAR,50);verify(provider,times(1)).embed(any());
        var corrected=analysisRepository.correct(source,first.currentRevision().id(),AnalysisFixtures.attributes(),Instant.now());
        retrieve.retrieve(source,OWNER,ProductTarget.MENSWEAR,50);verify(provider,times(2)).embed(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_embedding WHERE analysis_revision_id IN (?,?)",Integer.class,first.currentRevision().id(),corrected.currentRevision().id())).isEqualTo(2);
        assertThatThrownBy(() -> retrieve.retrieve(source,"b".repeat(64),ProductTarget.MENSWEAR,50)).hasMessage("SOURCE_NOT_FOUND");
        assertThatThrownBy(() -> retrieve.retrieve(source,OWNER,ProductTarget.MENSWEAR,0)).isInstanceOf(IllegalArgumentException.class);
        verify(provider,times(2)).embed(any());
    }
    @Test void productVersionsModelsDescriptorsAndVectorRoundTrip() {
        var f=fixture("BLUE");var first=products.ensure(f.product().id());var again=products.ensure(f.product().id());
        assertThat(again).isEqualTo(first);verify(provider,times(1)).embed(any());assertThat(first.data().vector().values()).hasSize(1536);assertThat(first.data().vector().values().getFirst()).isEqualTo(1f);
        jdbc.update("UPDATE product SET attributes='{\"primaryColors\":[\"NAVY\"]}',updated_at=updated_at+interval '1 second' WHERE id=?",f.product().id());
        var changed=products.ensure(f.product().id());assertThat(changed.data().descriptorHash()).isNotEqualTo(first.data().descriptorHash());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_embedding WHERE product_id=?",Integer.class,f.product().id())).isEqualTo(2);
        var descriptor=new EmbeddingDescriptorFactory().product(f.product());var source=new EmbeddingSource(EmbeddingSource.Kind.PRODUCT,f.product().id(),"test-version",f.product().updatedAt());
        var alternate=new EmbeddingModelMetadata("OPENAI","alternate-model",1536);var service=new EnsureEmbedding(embeddings,new DeterministicEmbeddingProvider(alternate),alternate,1,Duration.ZERO);
        var other=service.ensure(source,descriptor);assertThat(other.model()).isEqualTo(alternate);
        var next=new EmbeddingDescriptor("compatibility-descriptor-v2",descriptor.text().replace("compatibility-descriptor-v1","compatibility-descriptor-v2"));
        assertThat(service.ensure(source,next).descriptorVersion()).isEqualTo("compatibility-descriptor-v2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_embedding WHERE product_id=?",Integer.class,f.product().id())).isEqualTo(4);
    }
    @Test void concurrentGenerationMakesOneProviderCall() throws Exception {
        reset(provider);var f=fixture("BLUE");var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(provider.embed(any())).thenAnswer(call->{assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return new DeterministicEmbeddingProvider(model).embed(call.getArgument(0));});
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->products.ensure(f.product().id()));
            try {assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();var second=pool.submit(()->products.ensure(f.product().id()));
                assertThatThrownBy(()->second.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(EmbeddingException.class);
            } finally {release.countDown();}
            assertThat(first.get(10,TimeUnit.SECONDS).data().vector()).isNotNull();
        }
        verify(provider,times(1)).embed(any());assertThat(jdbc.queryForObject("SELECT count(*) FROM product_embedding",Integer.class)).isEqualTo(1);
    }
    @Test void boundedRetriesPermanentFailureAndDimensionMismatchNeverPersistVectors() {
        var f=fixture("BLUE");reset(provider);
        when(provider.embed(any())).thenThrow(new EmbeddingException("EMBEDDING_RATE_LIMITED",true));
        assertThatThrownBy(()->products.ensure(f.product().id())).hasMessage("EMBEDDING_RATE_LIMITED");verify(provider,times(2)).embed(any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_embedding",Integer.class)).isZero();
        reset(provider);when(provider.embed(any())).thenThrow(new EmbeddingException("EMBEDDING_INVALID_RESPONSE"));
        assertThatThrownBy(()->products.ensure(f.product().id())).hasMessage("EMBEDDING_INVALID_RESPONSE");verify(provider,times(1)).embed(any());
        reset(provider);when(provider.embed(any())).thenReturn(new EmbeddingProvider.Result(new EmbeddingVector(List.of(1f)),new EmbeddingModelMetadata("OPENAI",model.model(),1),null));
        assertThatThrownBy(()->products.ensure(f.product().id())).hasMessage("EMBEDDING_DIMENSION_MISMATCH");assertThat(jdbc.queryForObject("SELECT count(*) FROM product_embedding",Integer.class)).isZero();
        reset(provider);var descriptor=new EmbeddingDescriptorFactory().product(f.product());when(provider.embed(any())).thenThrow(new EmbeddingException("EMBEDDING_TIMEOUT",true)).thenReturn(new DeterministicEmbeddingProvider(model).embed(descriptor));
        assertThat(products.ensure(f.product().id()).data().vector()).isNotNull();verify(provider,times(2)).embed(any());
    }
    @Test void concurrentAnalysisGenerationAndDeterministicOfferSelection() throws Exception {
        var source=source();var revision=analysisRepository.findBySource(source).orElseThrow().currentRevision();
        reset(provider);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(provider.embed(any())).thenAnswer(call->{entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return new DeterministicEmbeddingProvider(model).embed(call.getArgument(0));});
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->analyses.ensure(revision));
            try {assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
                var second=pool.submit(()->analyses.ensure(revision));assertThatThrownBy(()->second.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(EmbeddingException.class);
            } finally {release.countDown();}
            assertThat(first.get(10,TimeUnit.SECONDS).analysisRevisionId()).isEqualTo(revision.id());
        }
        verify(provider,times(1)).embed(any());reset(provider);fake();
        var f=fixture("BLUE");products.ensure(f.product().id());var now=Instant.now();
        var alternative=catalogue.upsertOffer(new ProductOffer(new UUID(0,1),f.product().id(),f.market(),"fixture-other","Other","https://example.com/other",null,new Money(3999,"GBP"),AvailabilityStatus.UNKNOWN,null,true,now,now,now,now));
        var result=retrieve.retrieve(source,OWNER,ProductTarget.MENSWEAR,50);
        assertThat(result).hasSize(1);assertThat(result.getFirst().productOfferId()).isEqualTo(f.offer());
        jdbc.update("UPDATE product_offer SET availability_status='IN_STOCK' WHERE id=?",alternative.id());
        assertThat(retrieve.retrieve(source,OWNER,ProductTarget.MENSWEAR,50).getFirst().productOfferId()).isEqualTo(alternative.id());
        assertThatThrownBy(()->jdbc.update("UPDATE analysis_embedding SET analysis_revision_id=? WHERE analysis_revision_id=?",UUID.randomUUID(),revision.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void migrationColumnsKeysAndUpgradeOverV5PreserveChecksums() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6","7","8");
        assertThat(jdbc.queryForObject("SELECT format_type(atttypid,atttypmod) FROM pg_attribute WHERE attrelid='product_embedding'::regclass AND attname='embedding'",String.class)).isEqualTo("vector(1536)");
        var f=fixture("BLUE");products.ensure(f.product().id());
        assertThatThrownBy(()->jdbc.update("INSERT INTO product_embedding SELECT gen_random_uuid(),product_id,embedding_type,provider,model,dimensions,descriptor_version,source_version,descriptor_hash,source_updated_at,embedding,provider_request_id,created_at,updated_at,completed_at FROM product_embedding")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE product_embedding SET product_id=?",UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE product_embedding SET embedding='[1,0]'::vector")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var config=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).defaultSchema("embedding_upgrade").schemas("embedding_upgrade");
        config.target("5").load().migrate();
        var prior=jdbc.queryForList("SELECT checksum FROM embedding_upgrade.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",Integer.class);
        assertThat(prior).containsExactly(-929483003,-362163080,-360589483,-599056603,85417613);
        config.target("6").load().migrate();config.load().validate();
        assertThat(jdbc.queryForList("SELECT checksum FROM embedding_upgrade.flyway_schema_history WHERE version IN ('1','2','3','4','5') ORDER BY installed_rank",Integer.class)).isEqualTo(prior);
    }
}
