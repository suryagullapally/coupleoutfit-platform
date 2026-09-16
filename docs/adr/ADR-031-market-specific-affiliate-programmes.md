# ADR-031 — Market-specific affiliate programmes

Status: Accepted for Prompt 008.

AffiliateProgram belongs to RetailerMarket, not global Retailer. AffiliateFeed must reference the same market as its programme. Domain validation and composite foreign keys enforce this. Feed normalization loads currency and locale from that market's reference data, preserving ADR-001 and allowing future market-specific integrations without schema redesign.

See [affiliate feed ingestion foundation](../architecture/affiliate-feed-ingestion-foundation.md).
