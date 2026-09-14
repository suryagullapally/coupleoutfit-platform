package com.coupleoutfit.retailer.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

public record Retailer(UUID id, String slug, String name, RetailerStatus status, Instant createdAt, Instant updatedAt) {
    public Retailer {
        Objects.requireNonNull(id, "Retailer ID is required.");
        if (slug == null) throw new IllegalArgumentException("Retailer slug is required.");
        slug = slug.strip().toLowerCase(java.util.Locale.ROOT);
        if (slug.length() > 100 || !slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) throw new IllegalArgumentException("Retailer slug must be lowercase words separated by hyphens.");
        if (name == null || name.isBlank() || name.length() > 160) throw new IllegalArgumentException("Retailer name is required and limited to 160 characters.");
        name = name.strip();
        Objects.requireNonNull(status, "Retailer status is required.");
        Objects.requireNonNull(createdAt, "Creation time is required.");
        Objects.requireNonNull(updatedAt, "Update time is required.");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("Update time cannot precede creation time.");
    }
}
