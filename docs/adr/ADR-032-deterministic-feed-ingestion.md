# ADR-032 — Deterministic feed ingestion

Status: Accepted for Prompt 008.

CSV ingestion uses bounded, explicit field mappings and deterministic normalization; it never calls an LLM. Unsupported categories/targets are rejected and unknown fashion attributes remain absent. Provider reads and parsing stay outside bounded persistence transactions. Affiliate concepts do not enter analysis, compatibility, descriptors or ranking, preserving ADR-017.

See [affiliate feed ingestion foundation](../architecture/affiliate-feed-ingestion-foundation.md).
