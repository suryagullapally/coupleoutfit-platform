# Commercial catalogue foundation

Prompt 003 adds internal catalogue application boundaries and JPA persistence. There are no catalogue HTTP endpoints, feed adapters, scheduled jobs, URL fetchers, image processing, embeddings or matching implementations. Production catalogue tables start empty.

## Identity and market boundaries

Product is retailer-owned fashion identity: name, optional brand/subcategory, controlled category, MENSWEAR/WOMENSWEAR retailer classification, structured attributes and status. This classification does not describe a person's gender. Product contains no commercial prices, currency, URLs, availability or market defaults.

ProductOffer is the commercial listing of a Product in a RetailerMarket. Its authoritative identity is `(retailer_market_id, retailer_product_id)`, not a global retailer product ID or URL. Creation of a Product is explicit; cross-retailer or universal product deduplication is deferred. An existing offer cannot be reassigned to a different Product.

Retailer remains distinct from RetailerMarket. Country, Currency and Locale remain distinct. An offer's Product must belong to the retailer operating its RetailerMarket. Offer currency, when present, must equal that market's default currency. V3 adds supporting composite unique keys on RetailerMarket and Product; the offer's redundant `retailer_id` exists solely for composite foreign-key integrity. It is derived from Product inside the adapter, never accepted as an independent write input. No pricing/feed assumptions are added to Retailer.

Queries resolve the configured current MarketContext through the existing resolver. Active offer queries require active Product, offer, retailer, market and reference data, plus catalogueEnabled. They match all three context codes. Availability is independent: UNKNOWN and OUT_OF_STOCK are valid active listings. A future India market uses the same models with its own reference data and retailer-market rows; no country selector or alternate public launch market is added.

## Money and availability

Money is an immutable non-negative Java long `amountMinor` plus validated canonical ISO currency code, e.g. `{amountMinor: 4999, currencyCode: GBP}`. PostgreSQL uses bigint. No floating point, currency symbols, conversion or price history is used. A missing price is represented by a null Money; both database price columns must be null together. There is no inference from locale.

AvailabilityStatus is IN_STOCK, OUT_OF_STOCK or UNKNOWN. Offer active status is separate from stock. ProductVariant availability is separate from its parent offer and never automatically changes that offer's status.

## Attributes and variants

ProductAttributes wraps a deeply immutable, sorted map of bounded token lists. Allowed keys are primaryColors, secondaryColors, pattern, materialAppearance, fit, formality, seasonality and styleTags. Values are uppercase tokens up to 64 characters, at most 20 per key; duplicates are removed and ordering is deterministic. All fields use lists, including singular concepts, to keep one constrained representation. Arbitrary JSON objects and personal data are not accepted.

Hibernate maps this structure to a JSONB object. The product's attributeSchemaVersion is currently `1`; unknown versions are rejected. Variants reuse this version-1 attribute vocabulary, with size and color also available as explicit bounded text fields. No schema registry or speculative JSONB index exists.

ProductVariant belongs to one offer and may be absent entirely. Identified variants use `(offer_id, retailer_variant_id)` uniqueness and upsert. An unidentified variant requires explicit creation with a caller-generated UUID; its retailerVariantId remains null and it cannot use upsert. PostgreSQL permits multiple null identities. No identity is invented from size/color/SKU, and explicit unidentified creation does not promise deduplication across different UUIDs.

## Application and persistence

CatalogueWriter is the internal write entry point. CatalogueRepository exposes explicit creation, natural-key upserts and catalogue queries; CatalogueQueries resolves the current public market for active-offer reads. Domain records and application boundaries have no JPA/Spring dependencies. Three small infrastructure entities map to immutable domain values. No generic CRUD hierarchy or mapping framework is introduced.

The JPA adapter owns write transactions, loads canonical referenced records and calls domain invariants inside those transactions. Deliberately, it references existing retailer infrastructure mappings to validate canonical data without duplicating those mappings. This compromise is confined to infrastructure. Invalid domain values raise IllegalArgumentException; transactional reference/identity rejections raise sanitized CatalogueWriteException (database constraint violations remain framework persistence exceptions); no new public error surface exists. Existing HTTP Problem Details handling remains unchanged.

Offer writes lock their RetailerMarket row before looking up the natural identity, so concurrent first inserts serialize and return the same persisted ID. Variant writes use the same market-first lock order, then lock/refresh the parent offer. This coarse lock favors correctness for the MVP foundation; revisit throughput when feed ingestion is designed. Database uniqueness remains defense in depth. These guarantees apply to callers of the application boundary; direct SQL writers must obey database constraints and handle conflicts themselves.

Upserts preserve persisted UUID and createdAt, update mutable commercial/variant fields and reject older updatedAt values. Candidate UUIDs are used only on insertion. Timestamps are caller-supplied UTC Instants, persisted as timestamptz. Required references are checked. Offer and variant writes reject inactive retailer/market/reference data or disabled catalogue operation; Product identity creation only requires the retailer to exist. Disabling an offer through upsert is supported while its market is enabled; no market administration workflow is added.

Foreign keys restrict deletion while dependent rows exist; no cascading loss of offers or variants. Indexes cover retailer/product traversal and natural offer/variant identities, which also cover market/offer traversal. Composite supporting keys serve integrity, not speculative search.

## URLs, security and scope

URLs are trimmed, converted to ASCII URI representation and accepted only with HTTP(S), a host, no userinfo and a valid explicit port. No scheme is prepended, and path/query semantics are preserved. This is syntax validation only; URLs are never fetched, resolved or downloaded. Product, image and affiliate URLs use the same rule. Later ingestion must implement its own network safety policy.

Affiliate URLs remain internal persistence data. There is no public catalogue response, redirect or affiliate integration; ProductOffer's diagnostic string excludes all URLs. No catalogue data, affiliate values or full attributes are logged by this module. Existing security, correlation and Actuator behavior is preserved.

ADR-001 and ADR-005 enforce market-aware commercial separation. ADR-017 is preserved through generic catalogue concepts: no partner/couple-specific product types, compatibility logic, scoring or future modes are added. Future feed adapters can call CatalogueWriter, but none are implemented here.

## Verification

Domain tests cover validation, immutable attributes, money, URLs and market ownership. PostgreSQL Testcontainers cover JSONB/money round trips, natural-key upserts (including concurrent first writes), variant identity, market filtering, foreign keys/uniqueness/deletion restrictions, and a V2-to-V3 upgrade with original migration checksums. Existing platform tests exercise clean V1–V3 migration, reference data, health, headers and Problem Details. ArchUnit includes catalogue in domain/application dependency rules.
