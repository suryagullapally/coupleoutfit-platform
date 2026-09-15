# ADR-024 — pgvector candidate retrieval

Status: Accepted for Prompt 006.

pgvector performs candidate retrieval only. Exact cosine search over relationally eligible ProductOffers supplies a bounded semantic-similarity feature; this is not Couple Match Score or final user ranking. No ANN index is justified by the initial catalogue. HNSW requires later size/latency/recall evidence with market filters retained.

Product embeddings represent style, while returned candidates always identify concrete eligible offers. SQL applies market, target, category, status, availability and currency constraints before limiting results.
