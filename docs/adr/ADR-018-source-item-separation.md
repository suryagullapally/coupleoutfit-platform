# ADR-018 — SourceItem separation

Status: Accepted (approved Prompt 004).

SourceItem represents a user's supplied image or product URL independently of Product/ProductOffer. Catalogue-offer linkage is optional and never replaces the source or requires copying catalogue records. Each source persists its authoritative creation-time MarketContext and anonymous ownership reference. Generic source concepts preserve ADR-001 and ADR-017.
