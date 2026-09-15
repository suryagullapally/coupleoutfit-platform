# ADR-030 — Same-retailer market semantics

Status: Accepted for Prompt 007.

Same-retailer means the exact source RetailerMarket in the persisted market, never retailer name or domain alone. The restriction is applied before candidate LIMIT. It requires resolved catalogue provenance; uploads and unsupported URL fallbacks are rejected when the restriction is requested. Retailer remains distinct from RetailerMarket, preserving ADR-001.

See [matching and ranking foundation](../architecture/matching-ranking-foundation.md) for the exact v1 rules and limitations.
