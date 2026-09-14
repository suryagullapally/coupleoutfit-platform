# Source input foundation

Prompt 004 adds source inputs, private local image uploads and synchronous resolution against persisted catalogue data. It does not add analysis, matching, image fetching, scraping, feeds or affiliate redirects.

## Domain and persistence

SourceItem is independent of Product and ProductOffer. Every source has a generated UUID, a hashed anonymous ownership reference and a snapshot of the MarketContext resolved by the backend at creation. Request market fields cannot override it. Historical context is read from the source, not recalculated. Country/currency/locale foreign keys and the locale composite key preserve consistency. There are no partner-specific source concepts (ADR-017).

SourceAsset represents one primary private uploaded image per source, with an opaque generated key, independent status and server-derived metadata. Binary data never enters PostgreSQL. VERIFIED requires media type, positive byte size/dimensions, SHA-256 and verification time. `deletedAt` and DELETED status prepare for retention, but no deletion scheduler exists.

SourceUrl persists the original URL, conservative normalized URL, resolution outcome and optional retailer-market/offer/image references. A composite foreign key prevents an offer and retailer-market mismatch. Resolved sources remain SourceItems; no Products or ProductOffers are copied. V4 adds only source_item, source_asset and source_url, plus one supporting unique key on product_offer. V1–V3 are unchanged.

## State machine and transactions

Image: AWAITING_UPLOAD → UPLOAD_VERIFYING → READY_FOR_ANALYSIS, or FAILED after invalid content. Asset: PENDING → VERIFIED or REJECTED. Invalid state changes are rejected. READY_FOR_ANALYSIS is terminal here; no analysis is invoked.

URL: RESOLVING_URL → READY_FOR_ANALYSIS when a unique active catalogue offer supplies a usable image URL, otherwise IMAGE_UPLOAD_REQUIRED. The upload-authorization endpoint moves an unresolved URL source to AWAITING_UPLOAD and creates its one asset, preserving source ID, sourceType, URL provenance and market context.

Source/asset metadata creation is atomic. Object upload happens separately. Completion first locks the owned source and claims verification in a short transaction, reads/decodes outside the transaction, then atomically stores the verdict. Repeated completion of a verified source returns its existing success. Concurrent in-progress completion returns 409. A configurable two-minute lease allows recovery after an interrupted verifier. Claims use database-microsecond timestamps as fencing values so an expired worker cannot overwrite/release a newer claim. Transient storage/capacity failures release the claim back to AWAITING_UPLOAD; invalid content produces FAILED/REJECTED and attempts private object deletion.

Local originals are write-once, preventing replacement between verification and later use. A future production adapter must provide the same immutability/version binding. Failed sources require a new source; an awaiting upload can renew authorization without creating another asset. Completion without an uploaded object is a safe SOURCE_UPLOAD_NOT_FOUND error and marks the source failed.

## Storage and upload authorization

SourceAssetStorage defines authorization, head, bounded verification reads, deletion and temporary read URL creation, without provider SDK types. Keys are exactly `source-items/{sourceItemId}/{assetId}/original`; no names, filenames or retailer information appear.

LocalSourceAssetStorageAdapter runs only in local/test mode with `coupleoutfit.storage.provider=local`. It uses an ignored configured directory, path validation, symlink rejection and random 256-bit bearer capabilities. Upload tokens expire after 15 minutes by default, identify exactly one server-owned key and are revoked after one successful write. Reauthorization cannot overwrite an existing original. Temporary read tokens are also scoped and expiring; no read URL is returned by source status APIs. Deletion revokes capabilities for that key.

Local capabilities are process-local, bounded to 10,000 outstanding tokens per capability type, and discarded on restart. Expired entries are purged when issuing capabilities. An awaiting owned source can renew its upload authorization. Local uploads are serialized while streaming to bounded temporary files. This adapter is for local development/testing, not a distributed production storage service.

Local endpoints use tokens in query parameters because the existing correlation filter excludes queries from logs. No token-bearing paths or public filesystem routes are exposed. Uploaded metadata records the declared supported media type; completion independently checks the content. Local filesystem data must not be modified outside the adapter.

Production target remains private Cloudflare R2 with direct browser upload. No R2 SDK/adapter or credentials are configured in this prompt. Provider defaults to `disabled` outside local/test; image creation returns SOURCE_STORAGE_UNAVAILABLE (503). Setting local mode in staging/production, including mixed profiles, fails closed. Local upload/read controller mappings are absent there. Implement/configure R2 and its provider-enforced upload constraints, immutable object/version semantics and verification contract before enabling production images; do not turn the local proxy into production upload infrastructure.

## Verification limits

JPEG, PNG and WebP only. Configuration defines max bytes (10 MiB), max pixels (25 million), max dimension (10,000), and concurrent decoders (2). Configuration validation caps these safety ceilings. The verifier checks head metadata, bounds actual bytes independently, compares signature with declared media type, rejects truncated containers, checks dimensions before full decoding, rejects decoder warnings/corruption and computes SHA-256 from stored bytes. Hashes/paths are internal.

The JDK supplies JPEG/PNG decoding; the pinned TwelveMonkeys `imageio-webp` 3.13.1 plugin supplies WebP decoding ([upstream documentation](https://github.com/haraldk/TwelveMonkeys)). No SVG/GIF/HEIC or filename-based detection is used. Tiny generated JPEG/PNG/WebP fixtures exercise actual decoding. No OCR, image transformation, network access or global deduplication occurs.

## Anonymous ownership and browser security

The server generates a random 256-bit nonce, expiry and HMAC-SHA256 signature in an HttpOnly, SameSite=Strict cookie scoped to `/api/v1/source-items`. Production/staging enforce Secure and require a stable `SOURCE_SESSION_SIGNING_KEY` of at least 32 characters; provision cryptographically random secret material outside Git. Database ownership is only SHA-256 of the signed token. There is no personal information or session table. Missing, forged, expired or non-owning cookies receive the same 404. Cookie values are never included in JSON or logs.

Default cookie lifetime is seven days. Local/test can use a process-generated signing key; restarting then loses access to prior sessions. Set a stable development key through the environment if persistence across restarts is needed. Production instances must share the configured signing key.

All source POSTs require `X-Source-Request: 1`. Browser Origin, when present, must equal `SOURCE_PUBLIC_ORIGIN`, and cross-site Fetch Metadata is rejected. This protects cookie writes from cross-site form requests. Existing CORS origins/headers and credentials=false are unchanged, so browser integration must use a same-origin frontend proxy; this prompt does not implement that frontend. Bearer-authorized local PUT needs no cookie and cannot be submitted by a cross-origin form. Source responses and temporary reads use no-store; temporary reads are attachments with nosniff security headers.

## Supported URL resolution

CatalogueOfferUrlResolver depends only on application-facing catalogue/retailer ports. It compares conservatively normalized URLs among active current-market offers, accepts only a unique match with usable persisted primaryImageUrl, and never fetches a page or image. Ambiguous matches, missing images and inactive offers fall back. Current catalogue foundation lookup scans active market offers; add a purpose-built indexed lookup when feed volume warrants it, preserving these semantics.

Normalization permits HTTP(S), lowercases host/scheme, removes default ports/fragments and treats empty path as `/`. It preserves encoded paths, query parameters and meaningful trailing slashes. It rejects userinfo, IP literals, localhost/local hostnames and unsupported schemes. This is not DNS/redirect protection: there are no outbound resolvers. Before adding any supported outbound adapter, validate DNS-resolved addresses, block private/loopback/link-local/metadata ranges at connection time and revalidate every redirect to prevent rebinding/SSRF. No arbitrary remote-image proxy is provided.

A known active retailer host without a resolvable image returns SUPPORTED_RETAILER_URL_NOT_RESOLVABLE. Unknown hosts return SOURCE_URL_UNSUPPORTED. Both are normal 201 responses with IMAGE_UPLOAD_REQUIRED and action UPLOAD_IMAGE, not server errors. A persisted external image URL is only metadata for a future approved analysis boundary.

## API

- POST `/api/v1/source-items`: IMAGE_UPLOAD (no URL), or PRODUCT_URL (required URL); returns 201, source state, market and optional scoped upload details.
- GET `/api/v1/source-items/{id}`: owned status, safe asset metadata and URL resolution summary; no storage key/hash/image URL/session secret/affiliate URL.
- POST `/api/v1/source-items/{id}/upload-authorization`: creates fallback asset or renews an awaiting source's authorization.
- POST `/api/v1/source-items/{id}/upload-complete`: requires assetId; verifies ownership and content, then returns current state.
- Local/test only: PUT `/internal/local-upload?token=...`; GET `/internal/local-read?token=...`.

Invalid input is 400; missing/non-owned source or missing asset/object is 404; invalid state 409; expired authorization 410; oversize 413; unsupported media 415; transient unavailable storage/verification capacity 503. Source errors reuse sanitized Problem Details with code/traceId. Existing context, retailers, CORS, security headers and Actuator exposure are retained.

Logs contain source UUID, type/state, asset UUID, resolver/outcome and safe error codes. No image bytes, cookies, signatures, storage secrets, full URLs or sensitive query strings are logged. No additional metrics platform is introduced.

## Launch prerequisites and scope

Define anonymous source-image retention and implement deletion policy before production launch. Session expiry does not delete stored objects; uploaded images are not conceptually permanent. Configure private R2 and stable signing secrets, same-origin routing and production public origin before enabling uploads. No background retention job, accounts, analysis, embeddings, matching, feeds or affiliate redirects are implemented.
