# Market and retailer foundation (Prompt 002)

Java 21 / Spring Boot 4.1.1 remain unchanged. This feature adds only market reference data, retailer/storefront records and anonymous read APIs. No frontend integration or selector is implemented.

## Domain boundaries

- Country identifies a supported commerce country by uppercase two-letter code.
- Currency identifies an ISO currency and its nonnegative minor-unit precision. It contains no display symbol or price.
- Locale has a canonical BCP 47 code and explicit country/default-currency references. Currency is never inferred from a locale in application logic.
- MarketContext is an immutable, validated value record of country, currency and locale codes; it has no table.
- Retailer is market-independent: UUID, normalized stable slug, name, status and UTC timestamps.
- RetailerMarket is a retailer operating in a specific country/market. It owns its domain, explicit currency/locale, status and catalogue/matching enablement flags. These flags configure future capabilities; they do not implement them.

`Retailer != RetailerMarket`, `Country != Locale`, and `Currency != Locale`. Future market-specific commercial data must attach to RetailerMarket. “Same retailer” in later matching means the same retailer within an active market, never a domain string or a cross-country storefront.

Domain records have no Spring, JPA or API dependencies. The application layer exposes small read repository ports and precise query/resolution services. Adapters implement those ports with JPA EntityManager; public controllers return explicit immutable DTOs rather than persistence entities. Read-only transactions are placed on repository adapters, where database work happens. No generic CRUD hierarchy or admin write API exists.

The deliberate persistence compromise is separate small JPA entities with direct `toDomain` mappings. RetailerMarket's JPA associations reference market persistence entities inside infrastructure, preserving foreign-key semantics without duplicating cross-feature models in application/domain code. Explicit fetch joins avoid per-row relationship queries. Natural-code reference records carry database audit timestamps; retailer domain records expose Instant audit timestamps. IDs for future creation operations must be application-generated UUIDs. There is no automatic rename/update API for stable retailer slugs.

## Launch-market resolution

The single backend default configuration in application.yml is GB / GBP / en-GB. Environment overrides are `MARKET_DEFAULT_COUNTRY_CODE`, `MARKET_DEFAULT_CURRENCY_CODE`, and `MARKET_DEFAULT_LOCALE_CODE`. These are deployment settings, not public switching inputs.

MarketContextResolver loads all three canonical records, checks existence and active status, and verifies that the locale's country and explicitly configured default currency match the context. Missing, inactive, mismatched or malformed configuration fails; there is no fallback. An ApplicationRunner validates after Flyway and JPA initialization and before successful startup/readiness. Subsequent calls resolve again, so deactivation is not hidden behind a cached context.

Runtime configuration failures use HTTP 500 Problem Details with `MARKET_CONFIGURATION_INVALID` and the existing request-correlation convention. The feature-specific advice only handles this exception; platform error/security behavior remains intact. Startup logs carry a clear, sanitized configuration exception.

## Persistence and normalization

V1 is unchanged. V2 creates country, currency, locale, retailer and retailer_market and inserts exactly GB, GBP and en-GB. Retailer tables are intentionally empty. Foreign keys enforce existence; a composite locale/country/currency foreign key rejects inconsistent storefront combinations. `UNIQUE(retailer_id, country_code)` allows only one canonical storefront per retailer/country. Retailer slug is unique. Status, name, code, precision, hostname and timestamp checks provide database defence in depth.

The retailer/country unique index already supports that lookup. A country/status index supports active market queries; no speculative indexes or other business tables are added. Timestamp columns use timestamptz. Updates must explicitly advance updated_at; no database trigger or write workflow is introduced.

Slug normalization strips surrounding whitespace and lowercases using Locale.ROOT, then accepts alphanumeric segments separated by single hyphens (maximum 100 characters). Domain normalization strips surrounding whitespace and lowercases an ASCII DNS hostname (maximum 253 characters). Schemes, paths, ports, trailing dots and malformed labels are rejected. A hostname is storefront data, never retailer identity. This is field validation only, not URL parsing, URL resolution, scraping or SSRF handling.

## Read APIs

- `GET /api/v1/context` returns `{"market":{"countryCode":"GB","currencyCode":"GBP","localeCode":"en-GB"}}`.
- `GET /api/v1/retailers` returns `{"retailers":[]}` initially. Entries expose retailer ID, slug, name and market.countryCode only. Both retailer and storefront must be ACTIVE, their market references active, and country/currency/locale must match the resolved context. Results are ordered by slug. Capability flags do not override active status or implicitly enable matching.

Application queries also support retailer lookup by normalized slug and storefront lookup by retailer ID/country. These internal lookups can return inactive records; public listing applies active filtering. No selectable-market list, admin endpoints, secrets or feed configuration are exposed. Existing CORS, security headers, stateless policy and Actuator restrictions are unchanged.

## ADR-001 and ADR-017

UK-only public traffic is enforced through one configured launch context, without header, query-parameter, GeoIP or UI switching. Future India expansion can add Country/Currency/Locale records, retailer-market data and deployment/integration configuration using the same models. No India data is seeded. Future multi-market traffic selection would require an explicit later product decision, not a redesign of retailer or market identity.

Market and retailer infrastructure is neutral commerce infrastructure. ADR-017 remains intact: no person-relationship-specific names, compatibility engine, scoring, matching or future mode has been added. No Money, Price, Product, ProductOffer, feed, affiliate or AI implementation exists in this prompt.

## Verification

Unit tests cover domain validation and every resolver failure category. PostgreSQL Testcontainers tests cover V1/V2, canonical seed rows, foreign keys, uniqueness, invalid data, active-market queries, sanitized API failures and actual failed startup with invalid configuration. Test retailer/other-market fixtures are rolled back. ArchUnit enforces domain/application independence from API, infrastructure, Spring and JPA, and prevents APIs from depending on infrastructure.
