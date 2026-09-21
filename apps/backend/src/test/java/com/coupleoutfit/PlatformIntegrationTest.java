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
                .containsExactlyInAnyOrder("flyway_schema_history", "country", "currency", "locale", "retailer", "retailer_market", "product", "product_offer", "product_variant", "source_item", "source_asset", "source_url", "clothing_analysis", "analysis_revision", "product_embedding", "analysis_embedding", "match_session", "match_result", "affiliate_program", "affiliate_feed", "affiliate_feed_run", "purchase_link", "affiliate_click");
    }

    @Test void referenceDataAndMigrationHistoryExist() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class)).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
        assertThat(jdbc.queryForObject("SELECT name FROM country WHERE code='GB' AND active", String.class)).isEqualTo("United Kingdom");
        assertThat(jdbc.queryForObject("SELECT minor_unit_digits FROM currency WHERE code='GBP' AND active", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT code FROM locale WHERE country_code='GB' AND default_currency_code='GBP' AND active", String.class)).isEqualTo("en-GB");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM retailer", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM retailer_market", Integer.class)).isZero();
    }

    @Test void publicContextAndEmptyRetailers() throws Exception {
        mvc.perform(get("/api/v1/context").header("X-Request-Id", "market-test"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"market\":{\"countryCode\":\"GB\",\"currencyCode\":\"GBP\",\"localeCode\":\"en-GB\"}}"))
            .andExpect(header().string("X-Request-Id", "market-test"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/api/v1/retailers")).andExpect(status().isOk()).andExpect(jsonPath("$.retailers").isEmpty());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void runtimeMarketFailureUsesSafeProblemDetails() throws Exception {
        jdbc.update("UPDATE country SET active=false WHERE code='GB'");
        mvc.perform(get("/api/v1/context").header("X-Request-Id", "invalid-market"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("MARKET_CONFIGURATION_INVALID"))
            .andExpect(jsonPath("$.traceId").value("invalid-market"))
            .andExpect(jsonPath("$.detail").value("The public market is currently unavailable."));
    }

    @Autowired com.coupleoutfit.retailer.application.RetailerQueries retailerQueries;

    private java.util.UUID insertRetailer(String slug, String status) {
        var id = java.util.UUID.randomUUID();
        jdbc.update("INSERT INTO retailer VALUES (?, ?, 'Test Retailer', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", id, slug, status);
        return id;
    }
    private void insertMarket(java.util.UUID retailer, String status, String country, String currency, String locale) {
        jdbc.update("INSERT INTO retailer_market VALUES (?, ?, ?, ?, ?, 'shop.example.com', ?, false, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", java.util.UUID.randomUUID(), retailer, country, currency, locale, status);
    }

    @Test
    @org.springframework.transaction.annotation.Transactional
    void retailerQueriesFilterInactiveAndOtherMarkets() throws Exception {
        var active = insertRetailer("example", "ACTIVE");
        insertMarket(active, "ACTIVE", "GB", "GBP", "en-GB");
        insertMarket(insertRetailer("inactive", "INACTIVE"), "ACTIVE", "GB", "GBP", "en-GB");
        insertMarket(insertRetailer("inactive-market", "ACTIVE"), "INACTIVE", "GB", "GBP", "en-GB");
        jdbc.update("INSERT INTO country(code,name,active) VALUES ('US','United States',true)");
        jdbc.update("INSERT INTO currency(code,minor_unit_digits,active) VALUES ('USD',2,true)");
        jdbc.update("INSERT INTO locale(code,country_code,default_currency_code,active) VALUES ('en-US','US','USD',true)");
        insertMarket(active, "ACTIVE", "US", "USD", "en-US");
        assertThat(retailerQueries.bySlug(" EXAMPLE ")).isPresent();
        assertThat(retailerQueries.byRetailerAndCountry(active, "GB")).isPresent();
        assertThat(retailerQueries.byRetailerAndCountry(active, "IN")).isEmpty();
        mvc.perform(get("/api/v1/retailers")).andExpect(status().isOk())
            .andExpect(jsonPath("$.retailers.length()").value(1))
            .andExpect(jsonPath("$.retailers[0].id").value(active.toString()))
            .andExpect(jsonPath("$.retailers[0].slug").value("example"))
            .andExpect(jsonPath("$.retailers[0].market.countryCode").value("GB"))
            .andExpect(jsonPath("$.retailers[0].domain").doesNotExist());
    }

    @Test void databaseRejectsInvalidRelationshipsAndDuplicates() throws Exception {
        try (var connection = java.util.Objects.requireNonNull(jdbc.getDataSource()).getConnection(); var sql = connection.createStatement()) {
            connection.setAutoCommit(false);
            String id = java.util.UUID.randomUUID().toString();
            sql.executeUpdate("INSERT INTO retailer VALUES ('" + id + "', 'constraint-test', 'Test', 'ACTIVE', now(), now())");
            sql.executeUpdate("INSERT INTO retailer_market VALUES ('" + java.util.UUID.randomUUID() + "', '" + id + "', 'GB', 'GBP', 'en-GB', 'shop.example.com', 'ACTIVE', false, false, now(), now())");
            java.util.List<String> invalidSql = java.util.List.of(
                "INSERT INTO retailer_market VALUES ('" + java.util.UUID.randomUUID() + "', '" + id + "', 'GB', 'GBP', 'en-GB', 'other.example.com', 'ACTIVE', false, false, now(), now())",
                "INSERT INTO retailer_market VALUES ('" + java.util.UUID.randomUUID() + "', '" + java.util.UUID.randomUUID() + "', 'GB', 'GBP', 'en-GB', 'shop.example.com', 'ACTIVE', false, false, now(), now())",
                "INSERT INTO locale(code,country_code,default_currency_code,active) VALUES ('en-CA','CA','GBP',true)",
                "INSERT INTO locale(code,country_code,default_currency_code,active) VALUES ('en','GB','USD',true)",
                "INSERT INTO retailer VALUES ('" + java.util.UUID.randomUUID() + "', 'constraint-test', 'Duplicate', 'ACTIVE', now(), now())",
                "UPDATE retailer_market SET country_code='US' WHERE retailer_id='" + id + "'",
                "UPDATE currency SET minor_unit_digits=-1 WHERE code='GBP'",
                "UPDATE retailer SET status='UNKNOWN' WHERE id='" + id + "'",
                "UPDATE retailer_market SET domain='https://shop.example.com' WHERE retailer_id='" + id + "'"
            );
            for (String statement : invalidSql) {
                var savepoint = connection.setSavepoint();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> sql.executeUpdate(statement)).isInstanceOf(java.sql.SQLException.class);
                connection.rollback(savepoint);
            }
            // Existing but incompatible references must also fail the composite foreign key.
            sql.executeUpdate("INSERT INTO country(code,name,active) VALUES ('US','United States',true)");
            var savepoint = connection.setSavepoint();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> sql.executeUpdate("UPDATE retailer_market SET country_code='US' WHERE retailer_id='" + id + "'")).isInstanceOf(java.sql.SQLException.class);
            connection.rollback(savepoint);
            connection.rollback();
        }
    }

    @Test void invalidLaunchConfigurationFailsApplicationStartup() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            try (var ignored = new org.springframework.boot.builder.SpringApplicationBuilder(CoupleOutfitApplication.class)
                    .profiles("test").run("--server.port=0", "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(), "--spring.datasource.password=" + postgres.getPassword(),
                    "--coupleoutfit.market.default-currency-code=USD")) {
                throw new AssertionError("Invalid launch configuration must not start");
            }
        }).isInstanceOf(com.coupleoutfit.market.application.MarketConfigurationException.class);
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
