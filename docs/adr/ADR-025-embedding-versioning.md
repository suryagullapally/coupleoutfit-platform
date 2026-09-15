# ADR-025 — Embedding versioning

Status: Accepted for Prompt 006.

Embeddings are versioned by provider, model, descriptor version and source version. Product versions include the update timestamp and descriptor hash; analysis versions reference immutable revisions. Old rows are preserved and incompatible/stale versions are excluded from retrieval.

V6 uses vector(1536), with configuration and response validation enforcing dimensional agreement. Different dimensions require a later storage migration. Short-lived vectorless claims prevent concurrent duplicate generation without holding database transactions during provider calls. Crash recovery remains an explicit future operation.
