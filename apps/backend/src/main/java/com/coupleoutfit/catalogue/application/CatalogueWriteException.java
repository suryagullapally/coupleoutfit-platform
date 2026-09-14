package com.coupleoutfit.catalogue.application;

/** Sanitized internal write rejection; deliberately independent of persistence exceptions. */
public final class CatalogueWriteException extends RuntimeException {
    public CatalogueWriteException(String message) { super(message); }
}
