# Affiliate feed ingestion foundation

Prompt 008 adds internal, deterministic catalogue ingestion. No production retailers, programmes or feeds are seeded. No public import or catalogue endpoint is added.

## Market ownership and boundaries

`Retailer → RetailerMarket → AffiliateProgram → AffiliateFeed → AffiliateFeedRun` preserves ADR-001. A programme belongs to one retailer market. A feed explicitly references the same market; domain validation and a composite foreign key enforce this. Country, currency and locale remain separate reference data. Ingestion loads the feed's active market, currency minor-unit digits and canonical retailer domain; it contains no launch-country constants. India requires reference data and approved market/integration configuration, not a different schema.

Affiliate configuration lives in `affiliate`; ingestion execution lives in `feed`. Immutable domain records and application ports have no Spring, JPA or CSV dependencies. JDBC adapters implement configuration persistence, run lifecycle and bounded catalogue transactions, using the existing catalogue writer for Product/ProductOffer validation and JPA persistence. JDBC and JPA participate in the same Spring transaction. This deliberately avoids duplicate JPA models for integration configuration.

ADR-017 remains intact: these are generic commerce concerns. Analysis, compatibility and matching cannot depend on affiliate/feed packages (ArchUnit). No commission, programme or feed information enters descriptors or scoring. Exact retailer-market links preserve existing same-retailer filtering.

## Configuration and execution

AffiliateProgram holds UUID, retailer-market UUID, GENERIC network, optional external identifier, name, ACTIVE/PAUSED/DISABLED status and UTC timestamps. AffiliateFeed holds programme/market references, optional external identifier, CSV format, status, LOCAL_FILE source, relative source location, bounded field-to-header mapping and attempt/success timestamps. No credentials are stored.

`FeedImportService.run(feedId)` is the internal entry point. The local-only one-shot startup runner is enabled only by `coupleoutfit.feed.local-run-id`. The file provider is available in local/test profiles, never staging/production. It confines resolved paths to the configured root, rejects escaping symlinks, opens regular files and bounds reads even if a file grows after opening. Source references use simple relative `.csv` paths. A future approved remote provider must implement the content port with its own credential and network protections; this implementation never downloads, scrapes or resolves URLs.

Defaults under `coupleoutfit.feed`:

| Property | Default | Bound/meaning |
| --- | --- | --- |
| `batch-size` | 500 | 1–1000 rows per transaction |
| `max-bytes` | 67108864 | 64 MiB; configurable up to 1 GiB |
| `max-rejected-rows` | 1000 | Absolute rejection ceiling, 0–100000; checked after each batch including ownership conflicts |
| `local-root` | `.local/affiliate-feeds` | Relative to process working directory |

The streaming RFC 4180 reader uses Apache Commons CSV, strict UTF-8 with optional BOM, unique headers and at most 64 columns. Every configured header must be present; optional fields can be omitted from the mapping. Required mappings are retailer product ID, title, product URL, category and target. Price and currency mappings must be configured together. Unknown columns are ignored. Mis-shaped rows or mapped values exceeding 4096 characters are rejected. Invalid headers, encoding or unrecoverable quoting fail the run. Parsing and file I/O occur outside database transactions.

## Normalization and synchronization

Normalization uses explicit category, target, availability and fashion-token mappings. Unsupported target/category rows are rejected; unknown fashion attributes remain absent. There is no LLM or inferred fashion data. HTTP(S) product URLs must use the market's canonical host or its `www` form. Affiliate and image URLs require HTTP(S), a host and no embedded credentials. Query bytes are preserved, fragments removed, and URLs are never fetched or logged.

Price is parsed with BigDecimal and the currency's reference-data minor units, using exact integer conversion. Invalid, negative, overflowing or fractional-minor-unit values are rejected. Currency mismatch is rejected without FX conversion. An absent price is accepted as the existing null amount/null currency pair; a supplied mismatching currency is still rejected. No formatted symbols or floating-point money are stored.

Product owns normalized fashion identity: title, brand, category, target and attributes. ProductOffer owns the market listing: retailer product identifier, URLs, image URL, price, availability, timestamps and feed provenance. Variants are not synthesized. There is no cross-retailer deduplication.

The offer natural key is `(retailerMarketId, retailerProductId)`. Repeated rows/runs preserve Product and ProductOffer UUIDs and creation timestamps. Later valid duplicate rows update the same offer. A feed may adopt an unowned offer, but cannot take ownership from another feed. The market row lock serializes concurrent first inserts across feeds; the existing unique key is additional protection.

Unchanged Product values preserve its update timestamp. Commercial-only changes update the offer without changing the Product descriptor or invalidating its embedding. Changed Product/style values advance the existing Product source-version timestamp; prior vectors remain stored but no longer qualify as current. Embedding preparation stays separate through existing `EnsureProductEmbedding` missing/stale detection. No provider call occurs during ingestion.

## Runs, safety and staleness

Runs transition STARTED → PROCESSING → COMPLETED / COMPLETED_WITH_ERRORS / FAILED. STARTED may also fail. Terminal runs cannot restart; a new invocation creates a new run. A partial unique index permits only one STARTED/PROCESSING run per feed. Run creation commits before reading, and each bounded batch commits its rows and counters together. Previously committed rows survive later read failure. Retrying a failed database batch inside the same run is intentionally avoided.

Counters track read, accepted, rejected, created/updated products and offers, and stale offers. Rejections retain only aggregate safe error-code counts. A failure persists a safe code rather than exception text or raw feed rows. Run metrics use bounded format/status tags, never feed UUID tags. Logs contain run/feed/market identifiers, counts, duration and safe status/error codes; no feed bodies or tracking URLs.

Only a nonempty, completely accepted full snapshot reconciles absence. Offers owned by that feed and unseen in this run change from IN_STOCK to UNKNOWN. Already OUT_OF_STOCK remains OUT_OF_STOCK; UNKNOWN remains UNKNOWN. IDs, history and active flags are retained, and nothing is hard-deleted. UNKNOWN remains potentially eligible under existing retrieval rules; absence is uncertainty, not proof that an item is unavailable.

Failed runs, rejected snapshots and empty snapshots never reconcile absence. Other feeds' offers are never touched. `lastSeenAt` records the observing run's start, `lastSyncedAt` records the batch time, and last-seen run/feed foreign keys provide exact provenance. Only COMPLETED advances the feed success timestamp; empty completed runs still cannot stale offers.

A process crash can leave an active run requiring operator investigation and an explicit terminal failure update before retry. There is no automatic recovery, scheduler, queue or administrative endpoint in this foundation. Configuration should remain stable during an import; a new run reloads it.

## Migration and validation

V8 creates `affiliate_program`, `affiliate_feed`, `affiliate_feed_run` and adds nullable feed/run provenance to ProductOffer. Composite foreign keys enforce programme/feed/offer market and run ownership. Existing offer last-seen/synced fields are reused. V1–V7 are unchanged, and no business rows are seeded.

Tests cover clean migrations and V7 upgrades/checksums, constraints, bounded streaming/files, normalization, money, idempotency, ownership conflicts, concurrency, failed and partial snapshots, stale handling, commercial/style embedding behavior, market isolation and imported-catalogue retrieval/ranking. Test CSV files use fictitious hosts and transactional test records. Live affiliate integrations, scheduled imports, click redirects and conversion attribution are outside this prompt.
