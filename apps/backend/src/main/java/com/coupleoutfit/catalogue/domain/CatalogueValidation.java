package com.coupleoutfit.catalogue.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

final class CatalogueValidation {
    private CatalogueValidation() {}
    static String text(String value, int max, boolean required) {
        if (value == null && !required) return null;
        if (value == null || value.isBlank() || value.strip().length() > max) {
            throw new IllegalArgumentException("Invalid catalogue text.");
        }
        return value.strip();
    }
    static String url(String value, boolean required) {
        value = text(value, 4096, required);
        if (value == null) return null;
        try {
            var uri = new URI(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getPort() > 65535 || uri.getPort() == 0) throw new IllegalArgumentException();
            return uri.toASCIIString();
        } catch (Exception ignored) {
            // Never include a URL (which may carry affiliate credentials) in an error.
            throw new IllegalArgumentException("A valid HTTP(S) catalogue URL is required.");
        }
    }
    static void times(Instant created, Instant updated) {
        Objects.requireNonNull(created, "Creation time is required.");
        Objects.requireNonNull(updated, "Update time is required.");
        if (updated.isBefore(created)) throw new IllegalArgumentException("Update precedes creation.");
    }
}
