# ADR-033 — Feed absence is staleness, not deletion

Status: Accepted for Prompt 008.

Only nonempty, fully accepted snapshots reconcile offers owned by that feed. Missing IN_STOCK offers become UNKNOWN while preserving identity and history; OUT_OF_STOCK stays OUT_OF_STOCK. Failed, rejected or empty snapshots never trigger absence reconciliation. Offers are never hard-deleted by ingestion, and one feed cannot take another feed's provenance.

See [affiliate feed ingestion foundation](../architecture/affiliate-feed-ingestion-foundation.md).
