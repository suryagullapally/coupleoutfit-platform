# ADR-038 — Resolve the current approved affiliate destination

Status: Accepted for Prompt 009.

A purchase token identifies a stable ProductOffer through its MatchResult. Each navigation revalidates current offer/market/programme state and resolves the offer's current affiliate URL against exact approved network/retailer hosts. Browser input cannot override it. Clicks snapshot the session market and hash of the actual destination, retaining historical attribution while feed URLs evolve.

See [purchase-link architecture](../architecture/affiliate-click-and-purchase-link-foundation.md).
