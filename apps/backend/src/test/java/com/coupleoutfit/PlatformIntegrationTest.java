package com.coupleoutfit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PlatformIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres"));
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void contextLoadsAndFlywayEnablesPgvector() {
        assertThat(jdbc.queryForObject("SELECT extname FROM pg_extension WHERE extname = 'vector'", String.class))
                .isEqualTo("vector");
        assertThat(jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '1'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class))
                .containsExactly("flyway_schema_history");
    }

    @Test void healthEndpointsAreSafeAndHealthy() throws Exception {
        for (String path : new String[]{"/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist()).andExpect(header().exists("X-Request-Id"));
        }
    }

    @Test void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
        for (String endpoint : new String[]{"env", "beans", "heapdump", "configprops"}) {
            mvc.perform(get("/actuator/" + endpoint)).andExpect(status().isNotFound());
        }
    }
}
