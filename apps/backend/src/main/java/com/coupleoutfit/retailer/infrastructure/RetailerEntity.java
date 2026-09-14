package com.coupleoutfit.retailer.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.coupleoutfit.retailer.domain.Retailer;
import com.coupleoutfit.retailer.domain.RetailerStatus;

@Entity(name = "RetailerRecord")
@Table(name = "retailer")
public class RetailerEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 100, unique = true) private String slug;
    @Column(nullable = false, length = 160) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private RetailerStatus status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected RetailerEntity() {}
    public Retailer toDomain() { return new Retailer(id, slug, name, status, createdAt, updatedAt); }
}
