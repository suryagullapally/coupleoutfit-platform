# ADR-036 — Opaque server-resolved purchase tokens

Status: Accepted for Prompt 009.

Purchase capabilities use a 256-bit SecureRandom nonce and externally keyed HMAC-SHA-256 to produce a stable opaque bearer token. Only its SHA-256 hash and nonce are stored; recreating the bearer value requires the external secret. One link per final MatchResult prevents duplicate creation on repeated reads. Status and configurable expiry are checked server-side. Stable secret configuration is required outside local/test.

See [purchase-link architecture](../architecture/affiliate-click-and-purchase-link-foundation.md) for key lifecycle and local restart semantics.
