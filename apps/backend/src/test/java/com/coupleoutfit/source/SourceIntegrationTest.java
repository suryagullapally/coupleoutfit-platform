package com.coupleoutfit.source;

import com.coupleoutfit.source.application.*;
import com.coupleoutfit.source.api.AnonymousSourceSession;
import com.coupleoutfit.source.domain.*;
import com.coupleoutfit.catalogue.application.CatalogueWriter;
import com.coupleoutfit.catalogue.domain.*;
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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SourceIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SourceItemRepository repository;
    @Autowired CompleteSourceUpload completer;
    @Autowired SourceAssetStorage storage;
    @Autowired CatalogueWriter catalogue;
    private final JsonMapper json=JsonMapper.builder().build();
    record Created(JsonNode data,Cookie cookie) {}
    private Created create(String body) throws Exception {
        var result=mvc.perform(post("/api/v1/source-items").header("X-Source-Request","1").header("X-Request-Id","source-test")
                .contentType("application/json").content(body)).andExpect(status().isCreated())
                .andExpect(header().string("X-Request-Id","source-test")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        assertThat(result.getHeader("Set-Cookie")).contains("HttpOnly","SameSite=Strict").doesNotContain("Secure");
        String token=result.getHeader("Set-Cookie").split(";",2)[0].substring((AnonymousSourceSession.COOKIE+"=").length());
        assertThat(result.getContentAsString()).doesNotContain("storageKey","ownerSessionId","sha256","affiliateUrl",token,"source-items/");
        return new Created(json.readTree(result.getContentAsString()),new Cookie(AnonymousSourceSession.COOKIE,token));
    }
    private Created image() throws Exception { return create("{\"sourceType\":\"IMAGE_UPLOAD\"}"); }
    private void upload(Created c,String format) throws Exception {
        mvc.perform(put(c.data.path("upload").path("uploadUrl").asString()).contentType("image/"+format).content(SourceStorageTest.fixture(format)))
                .andExpect(status().isNoContent());
    }
    private org.springframework.test.web.servlet.ResultActions complete(Created c) throws Exception {
        return mvc.perform(post("/api/v1/source-items/"+c.data.path("sourceItemId").asString()+"/upload-complete")
                .cookie(c.cookie).header("X-Source-Request","1").contentType("application/json")
                .content("{\"assetId\":\""+c.data.path("asset").path("assetId").asString()+"\"}"));
    }
    @AfterEach void cleanup() {
        jdbc.queryForList("SELECT storage_key FROM source_asset",String.class).forEach(storage::deleteObject);
        jdbc.update("DELETE FROM source_url"); jdbc.update("DELETE FROM source_asset"); jdbc.update("DELETE FROM source_item");
        jdbc.update("DELETE FROM product_variant"); jdbc.update("DELETE FROM product_offer"); jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM retailer_market"); jdbc.update("DELETE FROM retailer");
    }
    @Test void imageLifecycleAndRepeatedCompletionPreservePrivateState() throws Exception {
        for(String format:List.of("png","jpeg","webp")) {
            var c=image(); assertThat(c.data.path("status").asString()).isEqualTo("AWAITING_UPLOAD");
            upload(c,format);
            complete(c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_FOR_ANALYSIS"))
                    .andExpect(jsonPath("$.asset.status").value("VERIFIED")).andExpect(jsonPath("$.asset.width").value(2))
                    .andExpect(jsonPath("$.asset.height").value(3)).andExpect(jsonPath("$.market.currencyCode").value("GBP"));
            complete(c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_FOR_ANALYSIS"));
            mvc.perform(get("/api/v1/source-items/"+c.data.path("sourceItemId").asString()).cookie(c.cookie))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.asset.mediaType").value("image/"+format))
                    .andExpect(jsonPath("$.asset.storageKey").doesNotExist()).andExpect(jsonPath("$.asset.sha256").doesNotExist());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_asset WHERE status='VERIFIED' AND sha256 IS NOT NULL",Integer.class)).isEqualTo(3);
    }
    @Test void anotherSessionCannotReadCompleteOrAuthorizeOwnedSource() throws Exception {
        var first=image(); var other=image(); String id=first.data.path("sourceItemId").asString();
        for(Cookie cookie:List.of(other.cookie,new Cookie(AnonymousSourceSession.COOKIE,"forged"))) {
            mvc.perform(get("/api/v1/source-items/"+id).cookie(cookie)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SOURCE_NOT_FOUND"));
            mvc.perform(post("/api/v1/source-items/"+id+"/upload-complete").cookie(cookie).header("X-Source-Request","1")
                    .contentType("application/json").content("{\"assetId\":\""+first.data.path("asset").path("assetId").asString()+"\"}"))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/v1/source-items/"+id+"/upload-authorization").cookie(cookie).header("X-Source-Request","1")).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/source-items/"+id)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/source-items/"+id+"/upload-complete").cookie(first.cookie).header("X-Source-Request","1")
                .contentType("application/json").content("{\"assetId\":\""+other.data.path("asset").path("assetId").asString()+"\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SOURCE_ASSET_NOT_FOUND"));
    }
    @Test void sourceWritesRequireExplicitSameOriginIntentAndTypeSpecificFields() throws Exception {
        mvc.perform(post("/api/v1/source-items").contentType("application/json").content("{\"sourceType\":\"IMAGE_UPLOAD\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SOURCE_REQUEST_FORBIDDEN"));
        mvc.perform(post("/api/v1/source-items").header("X-Source-Request","1").header("Sec-Fetch-Site","cross-site")
                .contentType("application/json").content("{\"sourceType\":\"IMAGE_UPLOAD\"}")).andExpect(status().isForbidden());
        for(String body:List.of("{\"sourceType\":\"PRODUCT_URL\"}","{\"sourceType\":\"IMAGE_UPLOAD\",\"url\":\"https://example.com\"}","{}")) {
            mvc.perform(post("/api/v1/source-items").header("X-Source-Request","1").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SOURCE_REQUEST_INVALID"));
        }
    }
    @Test void invalidUrlsReturnSafeProblemsAndUnsupportedUrlsFallBackOnSameSource() throws Exception {
        for(String url:List.of("file:///secret","javascript:alert(1)","data:text/html,x","http://127.0.0.1/metadata","https://user:secret@example.com")) {
            mvc.perform(post("/api/v1/source-items").header("X-Source-Request","1").header("X-Request-Id","url-invalid")
                    .contentType("application/json").content(json.writeValueAsString(Map.of("sourceType","PRODUCT_URL","url",url))))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("SOURCE_URL_INVALID")).andExpect(jsonPath("$.traceId").value("url-invalid"));
        }
        var c=create("{\"sourceType\":\"PRODUCT_URL\",\"url\":\"https://unknown.example.com/p?private=value\"}");
        assertThat(c.data.path("status").asString()).isEqualTo("IMAGE_UPLOAD_REQUIRED");
        assertThat(c.data.path("action").asString()).isEqualTo("UPLOAD_IMAGE");
        var fallback=mvc.perform(post("/api/v1/source-items/"+c.data.path("sourceItemId").asString()+"/upload-authorization")
                .cookie(c.cookie).header("X-Source-Request","1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AWAITING_UPLOAD"))
                .andReturn().getResponse();
        var pending=new Created(json.readTree(fallback.getContentAsString()),c.cookie);
        upload(pending,"png"); complete(pending).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_FOR_ANALYSIS"))
                .andExpect(jsonPath("$.sourceType").value("PRODUCT_URL"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_item",Integer.class)).isEqualTo(1);
    }
    private UUID catalogueFixture(boolean image) {
        UUID retailer=UUID.randomUUID(),market=UUID.randomUUID(); var now=Instant.now();
        jdbc.update("INSERT INTO retailer VALUES (?, 'source-test','Test','ACTIVE',?,?)",retailer,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        jdbc.update("INSERT INTO retailer_market VALUES (?,?,'GB','GBP','en-GB','shop.example.com','ACTIVE',true,false,?,?)",market,retailer,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        var product=catalogue.createProduct(new Product(UUID.randomUUID(),retailer,"Shirt",null,ProductCategory.SHIRT,null,ProductTarget.MENSWEAR,ProductAttributes.empty(),"1",ProductStatus.ACTIVE,now,now));
        return catalogue.upsertOffer(new ProductOffer(UUID.randomUUID(),product.id(),market,"product-1","Shirt","https://shop.example.com/p?id=1",
                "https://secret.example.com/affiliate",null,AvailabilityStatus.IN_STOCK,image?"https://shop.example.com/image.jpg":null,true,now,now,now,now)).id();
    }
    @Test void knownOfferResolvesWithoutCopyingProductOrFetchingRemoteImages() throws Exception {
        var offer=catalogueFixture(true);
        var c=create("{\"sourceType\":\"PRODUCT_URL\",\"url\":\"HTTPS://Shop.Example.com:443/p?id=1#image\"}");
        assertThat(c.data.path("status").asString()).isEqualTo("READY_FOR_ANALYSIS");
        assertThat(c.data.path("urlResolution").path("productOfferId").asString()).isEqualTo(offer.toString());
        assertThat(c.data.toString()).doesNotContain("secret.example.com","affiliateUrl","resolvedImageUrl");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_asset",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT normalized_url FROM source_url",String.class)).isEqualTo("https://shop.example.com/p?id=1");
        assertThat(jdbc.queryForObject("SELECT resolved_image_url FROM source_url",String.class)).isEqualTo("https://shop.example.com/image.jpg");
    }
    @Test void knownRetailerWithoutUsableOfferAndInactiveOffersFallBack() throws Exception {
        var offer=catalogueFixture(false);
        var missing=create("{\"sourceType\":\"PRODUCT_URL\",\"url\":\"https://shop.example.com/p?id=1\"}");
        assertThat(missing.data.path("status").asString()).isEqualTo("IMAGE_UPLOAD_REQUIRED");
        assertThat(missing.data.path("urlResolution").path("reason").asString()).isEqualTo("SUPPORTED_RETAILER_URL_NOT_RESOLVABLE");
        jdbc.update("UPDATE product_offer SET primary_image_url='https://shop.example.com/i.png',active=false WHERE id=?",offer);
        var inactive=create("{\"sourceType\":\"PRODUCT_URL\",\"url\":\"https://shop.example.com/p?id=1\"}");
        assertThat(inactive.data.path("status").asString()).isEqualTo("IMAGE_UPLOAD_REQUIRED");
        var unknown=create("{\"sourceType\":\"PRODUCT_URL\",\"url\":\"https://shop.example.com/unknown\"}");
        assertThat(unknown.data.path("status").asString()).isEqualTo("IMAGE_UPLOAD_REQUIRED");
    }
    @Test void rejectedUploadPersistsFailureAndDeletesUnsafeBytes() throws Exception {
        var c=image();
        mvc.perform(put(c.data.path("upload").path("uploadUrl").asString()).contentType("image/png").content("<html>fake</html>"))
                .andExpect(status().isNoContent());
        complete(c).andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("SOURCE_IMAGE_TYPE_UNSUPPORTED"));
        mvc.perform(get("/api/v1/source-items/"+c.data.path("sourceItemId").asString()).cookie(c.cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.asset.status").value("REJECTED"));
        String key=jdbc.queryForObject("SELECT storage_key FROM source_asset",String.class);
        assertThatThrownBy(() -> storage.headObject(key)).isInstanceOf(SourceException.class);
    }
    @Test void missingUploadIsSafeAndAuthorizationCanBeRenewedWithoutDuplicateAssets() throws Exception {
        var c=image();
        mvc.perform(post("/api/v1/source-items/"+c.data.path("sourceItemId").asString()+"/upload-authorization").cookie(c.cookie).header("X-Source-Request","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.asset.assetId").value(c.data.path("asset").path("assetId").asString()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_asset",Integer.class)).isEqualTo(1);
        complete(c).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SOURCE_UPLOAD_NOT_FOUND"));
    }
    @Test void concurrentCompletionCannotDuplicateOrCorruptState() throws Exception {
        var c=image(); upload(c,"png"); UUID id=UUID.fromString(c.data.path("sourceItemId").asString()),asset=UUID.fromString(c.data.path("asset").path("assetId").asString());
        String owner=jdbc.queryForObject("SELECT owner_session_id FROM source_item WHERE id=?",String.class,id);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<String> run=() -> {gate.await();try{return completer.complete(id,asset,owner).item().status().name();}catch(SourceException e){return e.code();}};
            var a=executor.submit(run);var b=executor.submit(run);gate.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).contains("READY_FOR_ANALYSIS")
                    .allMatch(s -> s.equals("READY_FOR_ANALYSIS") || s.equals("SOURCE_INVALID_STATE"));
        }
        assertThat(repository.findOwned(id,owner).asset().status()).isEqualTo(AssetStatus.VERIFIED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_asset",Integer.class)).isEqualTo(1);
    }
    @Test void transientVerificationFailureCanRetryAndInterruptedLeaseRecovers() throws Exception {
        var c=image(); upload(c,"png");
        UUID id=UUID.fromString(c.data.path("sourceItemId").asString()),asset=UUID.fromString(c.data.path("asset").path("assetId").asString());
        String owner=jdbc.queryForObject("SELECT owner_session_id FROM source_item WHERE id=?",String.class,id);
        var transientFailure=new CompleteSourceUpload(repository,(a,now) -> {throw new SourceException("SOURCE_STORAGE_UNAVAILABLE");},storage,java.time.Clock.systemUTC());
        assertThatThrownBy(() -> transientFailure.complete(id,asset,owner)).hasMessage("SOURCE_STORAGE_UNAVAILABLE");
        assertThat(repository.findOwned(id,owner).item().status()).isEqualTo(SourceStatus.AWAITING_UPLOAD);
        repository.beginVerification(id,asset,owner,Instant.now());
        assertThatThrownBy(() -> completer.complete(id,asset,owner)).hasMessage("SOURCE_INVALID_STATE");
        jdbc.update("UPDATE source_item SET created_at=now()-interval '10 minutes',updated_at=now()-interval '3 minutes' WHERE id=?",id);
        assertThat(completer.complete(id,asset,owner).item().status()).isEqualTo(SourceStatus.READY_FOR_ANALYSIS);
    }
    @Test void expiredVerifierCannotOverwriteNewClaim() throws Exception {
        var c=image(); upload(c,"png");
        UUID id=UUID.fromString(c.data.path("sourceItemId").asString()),asset=UUID.fromString(c.data.path("asset").path("assetId").asString());
        String owner=jdbc.queryForObject("SELECT owner_session_id FROM source_item WHERE id=?",String.class,id);
        var old=repository.beginVerification(id,asset,owner,Instant.now());
        jdbc.update("UPDATE source_item SET created_at=now()-interval '10 minutes',updated_at=now()-interval '3 minutes' WHERE id=?",id);
        var newer=repository.beginVerification(id,asset,owner,Instant.now());
        assertThatThrownBy(() -> repository.finishVerification(id,old.asset().rejected(),false,old.item().updatedAt(),Instant.now()))
                .hasMessage("SOURCE_INVALID_STATE");
        repository.releaseVerification(id,owner,old.item().updatedAt(),Instant.now());
        assertThat(repository.findOwned(id,owner).item().updatedAt()).isEqualTo(newer.item().updatedAt());
        assertThat(repository.findOwned(id,owner).asset().status()).isEqualTo(AssetStatus.PENDING);
    }
    @Test void persistedMarketIsAuthoritativeAndHistorical() throws Exception {
        var c=create("{\"sourceType\":\"IMAGE_UPLOAD\",\"countryCode\":\"US\",\"currencyCode\":\"USD\"}");
        assertThat(c.data.path("market").path("countryCode").asString()).isEqualTo("GB");
        assertThat(jdbc.queryForObject("SELECT country_code FROM source_item",String.class)).isEqualTo("GB");
        assertThat(jdbc.queryForObject("SELECT currency_code FROM source_item",String.class)).isEqualTo("GBP");
    }
    @Test void cleanAndExistingV3MigrationsPreserveAllPriorChecksums() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6","7","8","9");
        var config=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).defaultSchema("source_upgrade").schemas("source_upgrade");
        config.target("3").load().migrate();
        var checksums=jdbc.queryForList("SELECT checksum FROM source_upgrade.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",Integer.class);
        assertThat(checksums).containsExactly(-929483003,-362163080,-360589483);
        config.target("4").load().migrate(); config.load().validate();
        assertThat(jdbc.queryForList("SELECT checksum FROM source_upgrade.flyway_schema_history WHERE version IN ('1','2','3') ORDER BY installed_rank",Integer.class)).isEqualTo(checksums);
        assertThat(jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='source_upgrade'",String.class)).contains("source_item","source_asset","source_url");
    }
    @Test void foreignKeysCardinalityAndMeasurementConstraintsHold() throws Exception {
        var c=image(); String id=c.data.path("sourceItemId").asString(),asset=c.data.path("asset").path("assetId").asString();
        try(var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection();var sql=connection.createStatement()) {
            connection.setAutoCommit(false);
            for(String invalid:List.of(
                    "UPDATE source_item SET currency_code='USD' WHERE id='"+id+"'",
                    "UPDATE source_asset SET source_item_id='"+UUID.randomUUID()+"' WHERE id='"+asset+"'",
                    "UPDATE source_asset SET width=0 WHERE id='"+asset+"'",
                    "UPDATE source_asset SET status='VERIFIED' WHERE id='"+asset+"'",
                    "UPDATE source_asset SET storage_key='../../escape' WHERE id='"+asset+"'",
                    "DELETE FROM source_item WHERE id='"+id+"'",
                    "INSERT INTO source_asset SELECT '"+UUID.randomUUID()+"',source_item_id,storage_key,media_type,byte_size,width,height,sha256,status,created_at,verified_at,deleted_at FROM source_asset WHERE id='"+asset+"'")) {
                var save=connection.setSavepoint();assertThatThrownBy(() -> sql.executeUpdate(invalid)).isInstanceOf(java.sql.SQLException.class);connection.rollback(save);
            }
            connection.rollback();
        }
    }
}
