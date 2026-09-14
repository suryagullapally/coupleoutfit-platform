package com.coupleoutfit.retailer.infrastructure;

import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.retailer.application.RetailerRepository;
import com.coupleoutfit.retailer.domain.Retailer;
import com.coupleoutfit.retailer.domain.RetailerMarket;
import com.coupleoutfit.retailer.domain.RetailerStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class JpaRetailerRepository implements RetailerRepository {
    private static final String MARKETS = "select m from RetailerMarketRecord m join fetch m.retailer r join fetch m.country c join fetch m.defaultCurrency u join fetch m.locale l ";
    private final EntityManager entityManager;
    public JpaRetailerRepository(EntityManager entityManager) { this.entityManager = entityManager; }
    public Optional<Retailer> findBySlug(String slug) {
        return entityManager.createQuery("select r from RetailerRecord r where r.slug = :slug", RetailerEntity.class)
                .setParameter("slug", slug).getResultList().stream().findFirst().map(RetailerEntity::toDomain);
    }
    public Optional<RetailerMarket> findByRetailerAndCountry(UUID retailerId, String countryCode) {
        return entityManager.createQuery(MARKETS + "where r.id = :id and c.code = :country", RetailerMarketEntity.class)
                .setParameter("id", retailerId).setParameter("country", countryCode).getResultList().stream().findFirst().map(RetailerMarketEntity::toDomain);
    }
    public List<RetailerMarket> findActiveMarkets(MarketContext context) {
        return entityManager.createQuery(MARKETS + "where r.status = :active and m.status = :active and c.active = true and u.active = true and l.active = true and c.code = :country and u.code = :currency and l.code = :locale order by r.slug", RetailerMarketEntity.class)
                .setParameter("active", RetailerStatus.ACTIVE).setParameter("country", context.countryCode())
                .setParameter("currency", context.currencyCode()).setParameter("locale", context.localeCode())
                .getResultList().stream().map(RetailerMarketEntity::toDomain).toList();
    }
}
