# ADR-001 — UK-first, market-aware

Status: Accepted (approved Prompt 001).

## Decision

Launch in the United Kingdom. Future MVP defaults are GB, GBP and en-GB. Reusable architecture must remain market-aware without embedding these defaults as universal assumptions. Future expansion should primarily require market data, retailer-market configuration, affiliate programmes, feeds, catalogue and deployment configuration.

Market locale does not determine backend time storage: timestamps represent UTC instants.

## Scope

This foundation creates no Country, Currency, Locale, MarketContext or country selector implementation.
