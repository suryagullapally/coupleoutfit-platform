# ADR-005 — Product and ProductOffer separation

Status: Accepted (approved Prompt 003).

## Decision

Product represents retailer-owned fashion/catalogue identity and non-commercial attributes. ProductOffer represents its commercial listing within a RetailerMarket and owns price/currency, listing URLs, availability and synchronization metadata. ProductVariant belongs to ProductOffer.

A ProductOffer must reference a Product owned by the retailer of its RetailerMarket. The listing identity is unique per `(retailer_market_id, retailer_product_id)`; retailer product IDs are not globally unique. Money uses integer minor units plus a canonical currency code. Missing prices remain unknown.

Retailer is distinct from RetailerMarket. Market-specific commercial data must not be moved onto Product or Retailer. This preserves ADR-001's UK-first, market-aware structure and remains neutral to ADR-017's later compatibility engine.

## Consequences

The same retailer-owned product may have offers in different markets, each with its own commercial data. Universal deduplication is deferred. This decision adds no feed ingestion, affiliate redirect, matching or compatibility implementation.
