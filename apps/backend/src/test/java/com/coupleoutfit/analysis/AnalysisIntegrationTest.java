package com.coupleoutfit.analysis;

import com.coupleoutfit.analysis.application.*;
import com.coupleoutfit.analysis.domain.*;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="coupleoutfit.ai.clothing-analysis.retry-backoff=PT0S")
@AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class AnalysisIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired StartClothingAnalysis starter;
    @Autowired ClothingAnalysisRepository repository;
    @Autowired SourceAssetStorage storage;
    @MockitoBean ClothingAnalysisPort provider;
    private final JsonMapper json=JsonMapper.builder().build();
    record Source(UUID id,Cookie cookie) { String path(){return "/api/v1/source-items/"+id+"/analysis";} }
    private Source source(boolean ready) throws Exception {
        var response=mvc.perform(post("/api/v1/source-items").header("X-Source-Request","1").contentType("application/json")
                .content("{\"sourceType\":\"IMAGE_UPLOAD\"}")).andExpect(status().isCreated()).andReturn().getResponse();
        var data=json.readTree(response.getContentAsString());
        String token=response.getHeader("Set-Cookie").split(";",2)[0].substring((AnonymousSourceSession.COOKIE+"=").length());
        var result=new Source(UUID.fromString(data.path("sourceItemId").asText()),new Cookie(AnonymousSourceSession.COOKIE,token));
        if(ready) {
            try(var fixture=getClass().getResourceAsStream("/images/tiny.png")) {
                mvc.perform(put(data.path("upload").path("uploadUrl").asText()).contentType("image/png").content(Objects.requireNonNull(fixture).readAllBytes())).andExpect(status().isNoContent());
            }
            mvc.perform(post("/api/v1/source-items/"+result.id()+"/upload-complete").cookie(result.cookie()).header("X-Source-Request","1")
                    .contentType("application/json").content("{\"assetId\":\""+data.path("asset").path("assetId").asText()+"\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_FOR_ANALYSIS"));
        }
        return result;
    }
    private ResultActions start(Source s) throws Exception { return mvc.perform(post(s.path()).cookie(s.cookie()).header("X-Source-Request","1").header("X-Request-Id","analysis-test")); }
    private ResultActions correct(Source s,String parent,String attrs) throws Exception {
        return mvc.perform(post(s.path()+"/revisions").cookie(s.cookie()).header("X-Source-Request","1").contentType("application/json")
                .content("{\"parentRevisionId\":\""+parent+"\",\"attributes\":"+attrs+"}"));
    }
    @AfterEach void removePrivateFixtures() { jdbc.queryForList("SELECT storage_key FROM source_asset",String.class).forEach(storage::deleteObject); }
    @BeforeEach void fake() { when(provider.analyse(any())).thenAnswer(call -> {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var image=call.getArgument(0,AnalysisImage.class);assertThat(image.bytes()).isNotEmpty();
        assertThat(image.trustedImageUrl()).isNull();return AnalysisFixtures.result();
    }); }
    @Test void fullOwnedFlowIsIdempotentAndCorrectionsPreserveAiJson() throws Exception {
        var s=source(true);
        var response=start(s).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.currentRevision.revisionType").value("AI_GENERATED"))
                .andExpect(header().string("X-Request-Id","analysis-test")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("provider","promptVersion","model","imageUrl","base64","storageKey","ownerSessionId","raw");
        String parent=json.readTree(response).path("currentRevision").path("id").asText();
        start(s).andExpect(status().isOk()).andExpect(jsonPath("$.currentRevision.id").value(parent));
        String revised=AnalysisFixtures.ATTRIBUTES.replace("BURGUNDY","BLUE");
        correct(s,parent,revised).andExpect(status().isOk()).andExpect(jsonPath("$.currentRevision.revisionNumber").value(2))
                .andExpect(jsonPath("$.currentRevision.revisionType").value("USER_CORRECTED"));
        mvc.perform(get(s.path()).cookie(s.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.currentRevision.attributes.primaryColors[0]").value("BLUE"));
        correct(s,parent,revised).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_REVISION_CONFLICT"));
        verify(provider,times(1)).analyse(any());
        assertThat(jdbc.queryForObject("SELECT attributes->'primaryColors'->>0 FROM analysis_revision WHERE id=?",String.class,UUID.fromString(parent))).isEqualTo("BURGUNDY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analysis_revision WHERE clothing_analysis_id=(SELECT id FROM clothing_analysis WHERE source_item_id=?)",Integer.class,s.id())).isEqualTo(2);
        assertThat(repository.findBySource(s.id()).orElseThrow().currentRevision().attributes().primaryColors()).containsExactly("BLUE");
        assertThat(jdbc.queryForObject("SELECT country_code FROM source_item WHERE id=?",String.class,s.id())).isEqualTo("GB");
    }
    @Test void ownershipReadinessAndCorrectionValidation() throws Exception {
        var s=source(false);var other=source(false);
        start(s).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANALYSIS_SOURCE_NOT_READY"));
        mvc.perform(get(s.path()).cookie(s.cookie())).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
        var stolen=new Source(s.id(),other.cookie());
        start(stolen).andExpect(status().isNotFound());
        mvc.perform(get(stolen.path()).cookie(stolen.cookie())).andExpect(status().isNotFound());
        correct(stolen,UUID.randomUUID().toString(),AnalysisFixtures.ATTRIBUTES).andExpect(status().isNotFound());
        mvc.perform(post(s.path()).cookie(s.cookie())).andExpect(status().isForbidden());
        verifyNoInteractions(provider);
        var ready=source(true);var response=start(ready).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String parent=json.readTree(response).path("currentRevision").path("id").asText();
        correct(ready,parent,AnalysisFixtures.ATTRIBUTES.replace("BURGUNDY","sensitive inference")).andExpect(status().isBadRequest());
        correct(ready,parent,AnalysisFixtures.ATTRIBUTES.replace("\"subcategory\":\"MIDI_DRESS\",", "")).andExpect(status().isBadRequest());
        mvc.perform(post(ready.path()+"/revisions").cookie(ready.cookie()).header("X-Source-Request","1").contentType("application/json")
                .content("{\"parentRevisionId\":\""+parent+"\",\"attributes\":"+AnalysisFixtures.ATTRIBUTES+",\"model\":\"edited\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void transientFailuresAreBoundedAndPermanentFailuresNeverRetry() throws Exception {
        for(String code:List.of("ANALYSIS_TIMEOUT","ANALYSIS_PROVIDER_RATE_LIMITED","ANALYSIS_INVALID_RESPONSE","ANALYSIS_IMAGE_UNSUPPORTED")) {
            reset(provider);boolean retry=code.equals("ANALYSIS_TIMEOUT") || code.equals("ANALYSIS_PROVIDER_RATE_LIMITED");
            when(provider.analyse(any())).thenThrow(new AnalysisException(code,retry));var s=source(true);
            start(s).andExpect(status().is(code.equals("ANALYSIS_IMAGE_UNSUPPORTED")?422:503))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.traceId").value("analysis-test"));
            mvc.perform(get(s.path()).cookie(s.cookie())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
            start(s).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
            verify(provider,times(retry?2:1)).analyse(any());
            assertThat(repository.findBySource(s.id()).orElseThrow().currentRevision()).isNull();
        }
        reset(provider);when(provider.analyse(any())).thenThrow(new AnalysisException("ANALYSIS_TIMEOUT",true)).thenReturn(AnalysisFixtures.result());
        start(source(true)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));verify(provider,times(2)).analyse(any());
    }
    @Test void concurrentRequestsHaveOneProviderCallAndNoOpenTransaction() throws Exception {
        reset(provider);var s=source(true);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(provider.analyse(any())).thenAnswer(call -> {assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();return AnalysisFixtures.result();});
        String owner=jdbc.queryForObject("SELECT owner_session_id FROM source_item WHERE id=?",String.class,s.id());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> starter.start(s.id(),owner));
            try {
                assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
                var second=pool.submit(() -> starter.start(s.id(),owner));
                assertThat(second.get(5,TimeUnit.SECONDS).analysis().status()).isEqualTo(AnalysisStatus.IN_PROGRESS);
            } finally {release.countDown();}
            assertThat(first.get(10,TimeUnit.SECONDS).analysis().status()).isEqualTo(AnalysisStatus.COMPLETED);
        }
        verify(provider,times(1)).analyse(any());
    }
    @Test void databaseRejectsDuplicateAggregateRevisionMutationAndInvalidParent() throws Exception {
        var s=source(true);start(s).andExpect(status().isOk());var snapshot=repository.findBySource(s.id()).orElseThrow();
        var a=snapshot.analysis().id();var r=snapshot.currentRevision().id();
        for(String sql:List.of(
                "UPDATE analysis_revision SET schema_version='2' WHERE id='"+r+"'",
                "INSERT INTO clothing_analysis(id,source_item_id,status,created_at,updated_at) VALUES ('"+UUID.randomUUID()+"','"+s.id()+"','PENDING',now(),now())",
                "INSERT INTO analysis_revision SELECT '"+UUID.randomUUID()+"',clothing_analysis_id,revision_number,revision_type,parent_revision_id,attributes,confidence,schema_version,provider,model,provider_request_id,prompt_version,created_at FROM analysis_revision WHERE id='"+r+"'",
                "INSERT INTO analysis_revision(id,clothing_analysis_id,revision_number,revision_type,parent_revision_id,attributes,schema_version,created_at) VALUES ('"+UUID.randomUUID()+"','"+a+"',2,'USER_CORRECTED','"+UUID.randomUUID()+"','{}','1',now())",
                "UPDATE clothing_analysis SET current_revision_id='"+UUID.randomUUID()+"' WHERE id='"+a+"'"))
            assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void concurrentCorrectionsCannotBranchAndCrossAnalysisParentsAreRejected() throws Exception {
        var s=source(true);start(s).andExpect(status().isOk());
        var other=source(true);start(other).andExpect(status().isOk());
        var snapshot=repository.findBySource(s.id()).orElseThrow();var parent=snapshot.currentRevision().id();
        var foreign=repository.findBySource(other.id()).orElseThrow().currentRevision().id();
        assertThatThrownBy(() -> repository.correct(s.id(),foreign,AnalysisFixtures.attributes(),java.time.Instant.now())).hasMessage("ANALYSIS_REVISION_CONFLICT");
        assertThatThrownBy(() -> jdbc.update("UPDATE clothing_analysis SET current_revision_id=? WHERE id=?",foreign,snapshot.analysis().id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<String> correction=() -> {gate.await();try {repository.correct(s.id(),parent,AnalysisFixtures.attributes(),java.time.Instant.now());return "OK";}catch(AnalysisException e){return e.code();}};
            var first=pool.submit(correction);var second=pool.submit(correction);gate.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","ANALYSIS_REVISION_CONFLICT");
        }
        verify(provider,times(2)).analyse(any());
    }
    @Test void cleanAndV4UpgradePreservePriorChecksums() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5");
        var config=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).defaultSchema("analysis_upgrade").schemas("analysis_upgrade");
        config.target("4").load().migrate();
        var prior=jdbc.queryForList("SELECT checksum FROM analysis_upgrade.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank",Integer.class);
        assertThat(prior).containsExactly(-929483003,-362163080,-360589483,-599056603);
        config.target("5").load().migrate();config.load().validate();
        assertThat(jdbc.queryForList("SELECT checksum FROM analysis_upgrade.flyway_schema_history WHERE version IN ('1','2','3','4') ORDER BY installed_rank",Integer.class)).isEqualTo(prior);
    }
}
