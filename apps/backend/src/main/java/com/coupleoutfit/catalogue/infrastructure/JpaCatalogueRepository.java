package com.coupleoutfit.catalogue.infrastructure;

import com.coupleoutfit.catalogue.application.CatalogueRepository;
import com.coupleoutfit.catalogue.application.CatalogueWriteException;
import com.coupleoutfit.catalogue.domain.*;
import com.coupleoutfit.market.domain.MarketContext;
import com.coupleoutfit.retailer.infrastructure.RetailerEntity;
import com.coupleoutfit.retailer.infrastructure.RetailerMarketEntity;
import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Repository
@Transactional(readOnly = true)
public class JpaCatalogueRepository implements CatalogueRepository {
    private final EntityManager em;
    public JpaCatalogueRepository(EntityManager em) { this.em = em; }

    @Override @Transactional
    public Product createProduct(Product product) {
        required(em.find(RetailerEntity.class, product.retailerId()), "Retailer not found.");
        em.persist(new ProductEntity(product));
        em.flush();
        return product;
    }

    @Override @Transactional
    public Product updateProduct(Product product) {
        var existing=required(em.find(ProductEntity.class,product.id(),LockModeType.PESSIMISTIC_WRITE),"Product not found.");
        if(!existing.retailerId.equals(product.retailerId()) || !existing.createdAt.equals(product.createdAt()))
            throw new CatalogueWriteException("Product identity cannot be reassigned.");
        if(product.updatedAt().isBefore(existing.updatedAt))throw new CatalogueWriteException("Stale product update.");
        existing.update(product);em.flush();return existing.toDomain();
    }

    @Override @Transactional
    public ProductOffer upsertOffer(ProductOffer offer) {
        // Serialize natural-key writes per market, including a first insert with no offer row yet.
        var market = required(em.find(RetailerMarketEntity.class, offer.retailerMarketId(), LockModeType.PESSIMISTIC_WRITE), "Retailer market not found.").toDomain();
        var product = required(em.find(ProductEntity.class, offer.productId()), "Product not found.").toDomain();
        validateOffer(offer, product, market);
        var existing = offerEntity(offer.retailerMarketId(), offer.retailerProductId());
        ProductOfferEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            if (!entity.productId.equals(offer.productId())) throw new CatalogueWriteException("Offer identity cannot be reassigned to another product.");
            if (offer.updatedAt().isBefore(entity.updatedAt)) throw new CatalogueWriteException("Stale offer update.");
            entity.update(offer);
        } else {
            entity = new ProductOfferEntity(offer);
            entity.retailerId = product.retailerId();
            em.persist(entity);
        }
        em.flush();
        return entity.toDomain();
    }

    @Override @Transactional
    public ProductVariant upsertVariant(ProductVariant variant) {
        if (variant.retailerVariantId() == null) throw new CatalogueWriteException("Variant upsert requires a retailer variant ID.");
        lockVariantParent(variant.offerId());
        var matches = em.createQuery("select v from ProductVariantRecord v where v.offerId=:offer and v.retailerVariantId=:identity", ProductVariantEntity.class)
                .setParameter("offer", variant.offerId()).setParameter("identity", variant.retailerVariantId()).getResultList();
        ProductVariantEntity entity;
        if (matches.isEmpty()) {
            entity = new ProductVariantEntity(variant);
            em.persist(entity);
        } else {
            entity = matches.getFirst();
            if (variant.updatedAt().isBefore(entity.updatedAt)) throw new CatalogueWriteException("Stale variant update.");
            entity.update(variant);
        }
        em.flush();
        return entity.toDomain();
    }

    @Override @Transactional
    public ProductVariant createUnidentifiedVariant(ProductVariant variant) {
        if (variant.retailerVariantId() != null) throw new CatalogueWriteException("Identified variants must use upsert.");
        lockVariantParent(variant.offerId());
        em.persist(new ProductVariantEntity(variant));
        em.flush();
        return variant;
    }

    private void lockVariantParent(UUID offerId) {
        var offer = required(em.find(ProductOfferEntity.class, offerId), "Offer not found.");
        var market = required(em.find(RetailerMarketEntity.class, offer.retailerMarketId, LockModeType.PESSIMISTIC_WRITE), "Retailer market not found.").toDomain();
        em.refresh(offer, LockModeType.PESSIMISTIC_WRITE);
        validateOffer(offer.toDomain(), required(em.find(ProductEntity.class, offer.productId), "Product not found.").toDomain(), market);
    }

    private static void validateOffer(ProductOffer offer, Product product, com.coupleoutfit.retailer.domain.RetailerMarket market) {
        try {
            offer.validateFor(product, market);
        } catch (IllegalArgumentException rejected) {
            throw new CatalogueWriteException(rejected.getMessage());
        }
    }

    private Optional<ProductOfferEntity> offerEntity(UUID marketId, String identity) {
        return em.createQuery("select o from ProductOfferRecord o where o.retailerMarketId=:market and o.retailerProductId=:identity", ProductOfferEntity.class)
                .setParameter("market", marketId).setParameter("identity", identity).getResultList().stream().findFirst();
    }
    private static <T> T required(T value, String message) {
        if (value == null) throw new CatalogueWriteException(message);
        return value;
    }
    @Override public Optional<Product> findProduct(UUID id) {
        return Optional.ofNullable(em.find(ProductEntity.class, Objects.requireNonNull(id))).map(ProductEntity::toDomain);
    }
    @Override public Optional<ProductOffer> findOffer(UUID id) {
        return Optional.ofNullable(em.find(ProductOfferEntity.class, Objects.requireNonNull(id))).map(ProductOfferEntity::toDomain);
    }
    @Override public Optional<ProductOffer> findOfferByMarketAndRetailerProductId(UUID marketId, String retailerProductId) {
        return offerEntity(Objects.requireNonNull(marketId), Objects.requireNonNull(retailerProductId).strip()).map(ProductOfferEntity::toDomain);
    }
    @Override public List<ProductVariant> findVariantsByOfferId(UUID offerId) {
        return em.createQuery("select v from ProductVariantRecord v where v.offerId=:offer order by v.id", ProductVariantEntity.class)
                .setParameter("offer", Objects.requireNonNull(offerId)).getResultList().stream().map(ProductVariantEntity::toDomain).toList();
    }
    @Override public List<ProductOffer> findActiveOffers(MarketContext market) {
        return em.createQuery("""
                select o from ProductOfferRecord o, ProductRecord p, RetailerMarketRecord m
                where o.productId=p.id and o.retailerMarketId=m.id
                and o.active=true and p.status='ACTIVE' and m.status='ACTIVE' and m.retailer.status='ACTIVE'
                and m.catalogueEnabled=true and m.country.active=true and m.defaultCurrency.active=true and m.locale.active=true
                and m.country.code=:country and m.defaultCurrency.code=:currency and m.locale.code=:locale
                order by o.id
                """, ProductOfferEntity.class)
                .setParameter("country", market.countryCode()).setParameter("currency", market.currencyCode())
                .setParameter("locale", market.localeCode()).getResultList().stream().map(ProductOfferEntity::toDomain).toList();
    }
}
