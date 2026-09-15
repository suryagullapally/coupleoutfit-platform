# ADR-028 — MatchSession reproducibility

Status: Accepted for Prompt 007.

Persist source market, exact immutable analysis revision, target, mode, same-retailer restriction, ranking limits and algorithm version. Persist final ordered scores, component values and explanations atomically. Completed sessions cannot be rerun or reconfigured. Catalogue display data remains current; a full candidate attribute snapshot is not guaranteed.

See [matching and ranking foundation](../architecture/matching-ranking-foundation.md) for the exact v1 rules and limitations.
