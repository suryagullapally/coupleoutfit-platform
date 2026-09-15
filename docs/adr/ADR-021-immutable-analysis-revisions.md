# ADR-021 — Immutable analysis revisions

Status: Accepted for Prompt 005.

AI analysis and user corrections are immutable revisions within one ClothingAnalysis per SourceItem. Revision 1 is AI_GENERATED; subsequent USER_CORRECTED revisions retain a parent reference and sequential number. The aggregate points to its current revision. Corrections must name the current parent; stale writes return 409. Database uniqueness, same-aggregate foreign keys and an update-rejecting trigger defend this history.

This preserves the original AI result for reproducibility without requiring a history API or matching implementation. Future retention must account for the parent/current foreign keys.
