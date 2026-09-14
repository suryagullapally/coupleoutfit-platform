package com.coupleoutfit.catalogue;

import com.coupleoutfit.catalogue.application.*;
import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class CataloguePersistenceTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.2-pg17").asCompatibleSubstituteFor("postgres"));
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogueRepository repository;
    @Autowired CatalogueWriter writer;
    @Autowired CatalogueQueries queries;
    @Autowired EntityManager em;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    record Fixture(UUID retailer, UUID market, Product product) {}

    private Fixture fixture() {
        var retailer = UUID.randomUUID(); var market = UUID.randomUUID();
        jdbc.update("INSERT INTO retailer VALUES (?, ?, 'Test', 'ACTIVE', now(), now())", retailer, "test-" + retailer);
        jdbc.update("INSERT INTO retailer_market VALUES (?, ?, 'GB', 'GBP', 'en-GB', 'example.com', 'ACTIVE', true, false, now(), now())", market, retailer);
        var product = writer.createProduct(new Product(UUID.randomUUID(), retailer, "Oxford Shirt", "Example", ProductCategory.SHIRT,
                null, ProductTarget.MENSWEAR, new ProductAttributes(Map.of("primaryColors", List.of("NAVY"))), "1", ProductStatus.ACTIVE, NOW, NOW));
        return new Fixture(retailer, market, product);
    }
    private ProductOffer offer(Fixture f, Money price, String title, Instant time) {
        return new ProductOffer(UUID.randomUUID(), f.product.id(), f.market, "item-1", title,
                "https://example.com/item-1", "https://example.com/private?token=internal", price,
                AvailabilityStatus.UNKNOWN, "https://example.com/image.jpg", true, time, time, NOW, time);
    }
    private ProductVariant variant(UUID offer, String identity, String size, Instant time) {
        return new ProductVariant(UUID.randomUUID(), offer, identity, "SKU", size, "Navy",
                AvailabilityStatus.IN_STOCK, new ProductAttributes(Map.of("fit", List.of("REGULAR"))), time, NOW, time);
    }
    @Test @Transactional void offerUpsertRoundTripsJsonAndMoneyWithoutDuplicates() {
        var f = fixture();
        var first = writer.upsertOffer(offer(f, new Money(4999, "GBP"), "First", NOW));
        var updated = writer.upsertOffer(offer(f, new Money(5999, "GBP"), "Updated", NOW.plusSeconds(1)));
        assertThat(updated.id()).isEqualTo(first.id());
        assertThat(updated.createdAt()).isEqualTo(first.createdAt());
        em.clear();
        assertThat(repository.findOffer(first.id())).contains(updated);
        assertThat(repository.findProduct(f.product.id())).contains(f.product);
        assertThat(repository.findOfferByMarketAndRetailerProductId(f.market, "item-1")).contains(updated);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_offer", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attributes->'primaryColors'->>0 FROM product", String.class)).isEqualTo("NAVY");
        assertThat(updated.price()).isEqualTo(new Money(5999, "GBP"));
        var unknown = writer.upsertOffer(offer(f, null, "Unknown price", NOW.plusSeconds(2)));
        em.clear();
        assertThat(repository.findOffer(unknown.id()).orElseThrow().price()).isNull();
        assertThat(jdbc.queryForObject("SELECT amount_minor IS NULL AND currency_code IS NULL FROM product_offer", Boolean.class)).isTrue();
    }
    @Test @Transactional void variantsUpsertIndependentlyAndUnidentifiedCreationIsExplicit() {
        var f = fixture(); var offer = writer.upsertOffer(offer(f, null, "Shirt", NOW));
        assertThat(repository.findVariantsByOfferId(offer.id())).isEmpty();
        var first = writer.upsertVariant(variant(offer.id(), "variant-1", "S", NOW));
        var changed = writer.upsertVariant(variant(offer.id(), "variant-1", "M", NOW.plusSeconds(1)));
        assertThat(changed.id()).isEqualTo(first.id());
        em.clear();
        assertThat(repository.findVariantsByOfferId(offer.id())).containsExactly(changed);
        assertThat(repository.findOffer(offer.id()).orElseThrow().availabilityStatus()).isEqualTo(AvailabilityStatus.UNKNOWN);
        writer.createUnidentifiedVariant(variant(offer.id(), null, "L", NOW));
        writer.createUnidentifiedVariant(variant(offer.id(), null, "XL", NOW));
        assertThat(repository.findVariantsByOfferId(offer.id())).hasSize(3);
        assertThatThrownBy(() -> writer.upsertVariant(variant(offer.id(), null, "XXL", NOW))).isInstanceOf(CatalogueWriteException.class);
    }
    @Test @Transactional void currencyMismatchIsRejectedBeforePersistence() {
        var f = fixture();
        assertThatThrownBy(() -> writer.upsertOffer(offer(f, new Money(1, "USD"), "Invalid", NOW))).isInstanceOf(CatalogueWriteException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_offer", Integer.class)).isZero();
    }
    @Test @Transactional void retailerMismatchIsRejectedBeforePersistence() {
        var f = fixture(); var other = fixture();
        assertThatThrownBy(() -> writer.upsertOffer(offer(new Fixture(other.retailer, other.market, f.product), null, "Invalid", NOW)))
                .isInstanceOf(CatalogueWriteException.class);
    }
    @Test @Transactional void disabledMarketRejectsWrites() {
        var f = fixture(); jdbc.update("UPDATE retailer_market SET status='INACTIVE' WHERE id=?", f.market); em.clear();
        assertThatThrownBy(() -> writer.upsertOffer(offer(f, null, "Invalid", NOW))).isInstanceOf(CatalogueWriteException.class);
    }
    @Test @Transactional void offerIdentityCannotBeReassigned() {
        var f = fixture(); writer.upsertOffer(offer(f, null, "First", NOW));
        var otherProduct = writer.createProduct(new Product(UUID.randomUUID(), f.retailer, "Other", null, ProductCategory.TOP,
                null, ProductTarget.WOMENSWEAR, ProductAttributes.empty(), "1", ProductStatus.ACTIVE, NOW, NOW));
        assertThatThrownBy(() -> writer.upsertOffer(offer(new Fixture(f.retailer, f.market, otherProduct), null, "Reassign", NOW)))
                .isInstanceOf(CatalogueWriteException.class);
    }
    @Test @Transactional void naturalIdentityIsScopedToMarketAndActiveQueriesFilter() {
        var f = fixture(); var gb = writer.upsertOffer(offer(f, new Money(4999, "GBP"), "GB", NOW));
        jdbc.update("INSERT INTO country(code,name,active) VALUES ('US','United States',true)");
        jdbc.update("INSERT INTO currency(code,minor_unit_digits,active) VALUES ('USD',2,true)");
        jdbc.update("INSERT INTO locale(code,country_code,default_currency_code,active) VALUES ('en-US','US','USD',true)");
        var usMarket = UUID.randomUUID();
        jdbc.update("INSERT INTO retailer_market VALUES (?, ?, 'US', 'USD', 'en-US', 'example.com', 'ACTIVE', true, false, now(), now())", usMarket, f.retailer);
        var us = writer.upsertOffer(offer(new Fixture(f.retailer, usMarket, f.product), new Money(6999, "USD"), "US", NOW));
        assertThat(us.id()).isNotEqualTo(gb.id());
        assertThat(queries.activeOffers()).containsExactly(gb);
        assertThat(repository.findActiveOffers(new MarketContext("US", "USD", "en-US"))).containsExactly(us);
        jdbc.update("UPDATE product SET status='INACTIVE' WHERE id=?", f.product.id()); em.clear();
        assertThat(queries.activeOffers()).isEmpty();
        jdbc.update("UPDATE product SET status='ACTIVE' WHERE id=?", f.product.id());
        jdbc.update("UPDATE retailer_market SET catalogue_enabled=false WHERE id=?", f.market); em.clear();
        assertThat(queries.activeOffers()).isEmpty();
    }
    @Test void concurrentFirstUpsertsUseOneIdentity() throws Exception {
        var f = fixture();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            Callable<ProductOffer> write = () -> { gate.await(); return writer.upsertOffer(offer(f, null, "Concurrent", NOW)); };
            var first = executor.submit(write); var second = executor.submit(write); gate.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS).id()).isEqualTo(second.get(20, TimeUnit.SECONDS).id());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM product_offer WHERE retailer_market_id=?", Integer.class, f.market)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM product_offer WHERE retailer_market_id=?", f.market);
            jdbc.update("DELETE FROM product WHERE id=?", f.product.id());
            jdbc.update("DELETE FROM retailer_market WHERE id=?", f.market);
            jdbc.update("DELETE FROM retailer WHERE id=?", f.retailer);
        }
    }
    @Test void upgradeFromV2PreservesChecksumsAndCreatesOnlyThreeTables() {
        var configured = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .defaultSchema("upgrade_test").schemas("upgrade_test");
        configured.target("2").load().migrate();
        assertThat(jdbc.queryForObject("SELECT checksum FROM upgrade_test.flyway_schema_history WHERE version='1'", Integer.class)).isEqualTo(-929483003);
        assertThat(jdbc.queryForObject("SELECT checksum FROM upgrade_test.flyway_schema_history WHERE version='2'", Integer.class)).isEqualTo(-362163080);
        configured.target("3").load().migrate();
        configured.load().validate();
        assertThat(jdbc.queryForList("SELECT version FROM upgrade_test.flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank", String.class)).containsExactly("1", "2", "3");
        assertThat(jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='upgrade_test'", String.class))
                .containsExactlyInAnyOrder("flyway_schema_history", "country", "currency", "locale", "retailer", "retailer_market", "product", "product_offer", "product_variant");
    }
    @Test void databaseRejectsInvalidCommercialRelationshipsAndOrphanDeletion() throws Exception {
        var f = fixture(); var o = writer.upsertOffer(offer(f, new Money(4999, "GBP"), "Shirt", NOW));
        var v = writer.upsertVariant(variant(o.id(), "variant-1", "S", NOW));
        try (var connection = Objects.requireNonNull(jdbc.getDataSource()).getConnection(); var sql = connection.createStatement()) {
            connection.setAutoCommit(false);
            var invalid = List.of(
                "UPDATE product_offer SET amount_minor=-1 WHERE id='"+o.id()+"'",
                "UPDATE product_offer SET currency_code=NULL WHERE id='"+o.id()+"'",
                "UPDATE product_offer SET currency_code='USD' WHERE id='"+o.id()+"'",
                "UPDATE product_offer SET retailer_id='"+UUID.randomUUID()+"' WHERE id='"+o.id()+"'",
                "UPDATE product_offer SET product_id='"+UUID.randomUUID()+"' WHERE id='"+o.id()+"'",
                "UPDATE product_offer SET retailer_market_id='"+UUID.randomUUID()+"' WHERE id='"+o.id()+"'",
                "UPDATE product_variant SET offer_id='"+UUID.randomUUID()+"' WHERE id='"+v.id()+"'",
                "UPDATE product SET category='UNKNOWN' WHERE id='"+f.product.id()+"'",
                "UPDATE product SET attributes='[]' WHERE id='"+f.product.id()+"'",
                "INSERT INTO product_offer SELECT '"+UUID.randomUUID()+"', product_id, retailer_market_id, retailer_id, retailer_product_id, title, product_url, affiliate_url, amount_minor, currency_code, availability_status, primary_image_url, active, last_seen_at, last_synced_at, created_at, updated_at FROM product_offer WHERE id='"+o.id()+"'",
                "INSERT INTO product_variant SELECT '"+UUID.randomUUID()+"', offer_id, retailer_variant_id, sku, size, color, availability_status, attributes, last_synced_at, created_at, updated_at FROM product_variant WHERE id='"+v.id()+"'",
                "DELETE FROM product_offer WHERE id='"+o.id()+"'",
                "DELETE FROM product WHERE id='"+f.product.id()+"'",
                "DELETE FROM retailer_market WHERE id='"+f.market+"'"
            );
            for (var statement : invalid) {
                var savepoint = connection.setSavepoint();
                assertThatThrownBy(() -> sql.executeUpdate(statement)).as(statement.split(" ")[0]).isInstanceOf(java.sql.SQLException.class);
                connection.rollback(savepoint);
            }
            // An existing but incompatible currency must also fail the composite FK.
            sql.executeUpdate("INSERT INTO currency(code,minor_unit_digits,active) VALUES ('USD',2,true)");
            var savepoint = connection.setSavepoint();
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE product_offer SET currency_code='USD' WHERE id='"+o.id()+"'")).isInstanceOf(java.sql.SQLException.class);
            connection.rollback(savepoint);
            connection.rollback();
        } finally {
            jdbc.update("DELETE FROM product_variant WHERE offer_id=?", o.id());
            jdbc.update("DELETE FROM product_offer WHERE id=?", o.id());
            jdbc.update("DELETE FROM product WHERE id=?", f.product.id());
            jdbc.update("DELETE FROM retailer_market WHERE id=?", f.market);
            jdbc.update("DELETE FROM retailer WHERE id=?", f.retailer);
        }
    }
}
