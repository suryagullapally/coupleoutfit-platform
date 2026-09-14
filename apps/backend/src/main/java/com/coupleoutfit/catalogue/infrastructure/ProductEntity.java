package com.coupleoutfit.catalogue.infrastructure;

import com.coupleoutfit.catalogue.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Entity(name = "ProductRecord")
@Table(name = "product")
class ProductEntity {
    @Id UUID id;
    UUID retailerId;
    @Column(length = 300) String canonicalName;
    @Column(length = 160) String brand;
    @Enumerated(EnumType.STRING) ProductCategory category;
    @Column(length = 64) String subcategory;
    @Enumerated(EnumType.STRING) ProductTarget target;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") Map<String, List<String>> attributes;
    @Column(length = 16) String attributeSchemaVersion;
    @Enumerated(EnumType.STRING) ProductStatus status;
    Instant createdAt;
    Instant updatedAt;
    protected ProductEntity() {}
    ProductEntity(Product value) {
        id = value.id();
        createdAt = value.createdAt();
        update(value);
    }
    void update(Product value) {
        retailerId = value.retailerId();
        canonicalName = value.canonicalName();
        brand = value.brand();
        category = value.category();
        subcategory = value.subcategory();
        target = value.target();
        attributes = value.attributes().values();
        attributeSchemaVersion = value.attributeSchemaVersion();
        status = value.status();
        updatedAt = value.updatedAt();
    }
    Product toDomain() {
        return new Product(id, retailerId, canonicalName, brand, category, subcategory, target, new ProductAttributes(attributes), attributeSchemaVersion, status, createdAt, updatedAt);
    }
}
