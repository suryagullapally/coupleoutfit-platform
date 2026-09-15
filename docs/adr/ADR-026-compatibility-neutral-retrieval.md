# ADR-026 — Compatibility-neutral retrieval

Status: Accepted for Prompt 006.

Candidate retrieval is reusable Outfit Compatibility Engine infrastructure, not partner-specific infrastructure. Immutable CompatibilityRequest and CompatibilityCandidate carry explicit market/target constraints and deterministic features. Source market remains persisted SourceItem context; target is supplied by the caller, never inferred from a person.

No public compatibility endpoint, MatchSession, final scoring, mode weights or future UX mode is introduced. These foundations preserve ADR-001 and ADR-017.
