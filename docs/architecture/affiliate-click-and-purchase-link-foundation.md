# Affiliate clicks and purchase links

Prompt 009 adds anonymous outbound purchase capabilities and minimal click attribution. Matching, embeddings, feed synchronization and scoring retain their existing responsibilities. There are no conversions, commissions, dashboards, public administration endpoints or frontend changes.

## Public contract and authorization

An owner-authorized MatchSession response includes a `purchase` object on each final result:

```json
{"purchase":{"available":true,"url":"/go/<opaque-token>"}}
```

Unavailable results return `{"available":false,"url":null}`. The match API never includes the affiliate URL, programme configuration, token hash, nonce or click ID. Purchase eligibility does not affect ranking or Couple Match Score. Links are created lazily only for persisted final results, after existing owner authorization. Repeated GETs reuse the same active link; concurrent requests serialize on the result and link rows. No links are generated for discarded candidates.

The token is a bearer capability for redirect only. `GET /go/{token}` requires no cookie, making copied links usable. MatchSession and SourceItem APIs remain owner-protected. The response URL is relative to the website origin; the website/reverse proxy must route `/go/*` to the backend. No JavaScript redirect is required.

## Token design, reuse and lifecycle

Each PurchaseLink has a UUID, a 256-bit SecureRandom nonce, a SHA-256 token hash, MatchResult/MatchSession/ProductOffer references, ACTIVE/REVOKED status, creation/expiry and optional last-use timestamp. Composite foreign keys enforce result/session/offer identity. The result ID is unique, so there is at most one stored link per result.

The 43-character Base64URL token is HMAC-SHA-256 of a versioned purpose prefix and the random nonce under an external secret. This deliberately combines server-stored lookup with reproducible bearer values: the database stores only the nonce and token hash, never the raw token or affiliate URL. The nonce alone cannot recreate the token without the external key. Redirect lookup hashes the supplied token; it does not decode client claims. The raw token contains no IDs, URL or market information.

Configure `PURCHASE_TOKEN_KEY` as Base64 encoding of at least 32 cryptographically random bytes. It is required in staging/production and must remain stable and shared across instances. Store it in deployment secret configuration, never in source or database. Local/test may omit it and receive an ephemeral process key. For locally reusable match URLs across restarts, configure a stable local key too.

Changing the key prevents reissuing old bearer values from match responses; their existing hash-lookup URLs remain usable until expiry/revocation. The implementation fails closed for match response reuse when the key no longer matches the stored hash. Coordinated key rotation/revocation is production operational work, not an automatic token refresh mechanism.

`coupleoutfit.purchase.lifetime` defaults to P30D and is bounded to more than zero and at most 90 days. Expiry is derived from time. Expired/revoked links are not automatically replaced on repeated match GETs, preventing unbounded token creation. An internal `PurchaseLinks.revoke(id)` supports explicit revocation; no admin API is added. Concurrent revocation and redirect serialize on the link row.

## Programme and destination policy

A purchasable offer must have approved integration provenance through `lastAffiliateFeedId → AffiliateFeed → AffiliateProgram`. Unowned offers remain displayable but non-purchasable. No duplicate programme relationship is added to ProductOffer.

V9 adds a bounded `approved_destination_hosts` array to AffiliateProgram. It defaults empty. Internal `PurchaseLinks.approveDestinationHosts(programId, hosts)` validates and stores up to 20 exact hosts; no wildcard or executable configuration is accepted. No network hosts or production integrations are seeded. An ACTIVE programme permits its market's canonical retailer host/`www` form plus explicitly approved network hosts. A network destination need not equal the retailer host.

`AffiliateDestinationPolicy` requires HTTP(S), a bounded ASCII DNS hostname, no embedded credentials, no fragments, no control characters or backslashes, and default HTTP/HTTPS ports. Literal IPs, localhost/private naming suffixes, unknown hosts and suffix-spoofing are rejected. Scheme/host case is normalized while raw path and query bytes, including affiliate tracking parameters, are preserved. SHA-256 records the normalized actual destination URL.

There is no DNS resolution, HTTP fetch or redirect-chain following on the click path. The policy trusts explicitly approved hosts and their configured network redirects; their onboarding/DNS security remains an operational responsibility. Browser query parameters cannot select or override a destination. The only destination source is the current ProductOffer affiliate URL. There is no product URL fallback.

## Click transaction and current-state checks

The short transaction locks the PurchaseLink, checks status/expiry, reads the immutable result and current offer/integration, validates the destination, inserts AffiliateClick, and updates only the link last-used timestamp. It commits before the controller returns HTTP 302. It never calls AI, embeds, ranks, ingests feeds or mutates catalogue/feed data. Market locks precede catalogue row locks to follow the existing writer order.

At click time the following must still agree:

- MatchResult's session and offer match the PurchaseLink.
- MatchSession country/currency/locale equal the RetailerMarket context.
- An optional offer currency equals the session currency.
- Feed/programme provenance belongs to that market.
- Offer, Product, Retailer, RetailerMarket and market reference data remain active.
- AffiliateProgram remains ACTIVE; PAUSED and DISABLED both stop new redirects.
- The current affiliate URL passes the current approved-host policy.

A feed's PAUSED state stops ingestion, not clicks; programme status controls commercial shutdown. Catalogue/matching enablement flags remain ingestion/retrieval concerns, not purchase authorization.

IN_STOCK, UNKNOWN and OUT_OF_STOCK all permit redirect when the integration and URL remain usable: retailer availability may be fresher. Missing prices are allowed and never fabricated. Expired/inactive/unsafe links fail safely without redirect. Updating the affiliate URL for the same offer changes the next redirect target without changing the token or MatchResult.

Click storage failures return a safe 503 and no Location header. Tracking integrity takes precedence over untracked traffic. A commit followed by a client disconnect can still leave a click record: REDIRECTED means a redirect was committed for issuance, not proof that the browser reached the retailer or purchased anything.

## AffiliateClick, attribution and privacy

Each immutable click records a stable UUID; purchase link, match session/result, offer, retailer-market and programme references; session country/currency/locale snapshot; sanitized request correlation ID; destination host and SHA-256 URL hash; REDIRECTED status; and UTC creation/redirect timestamps.

Algorithm, match mode and score attribution can be joined from the immutable session/result. Historical market is copied from that session, never the current launch-market resolver. Restrictive foreign keys preserve click history and block deletion of referenced commerce records; no cascade deletion is used.

No raw token, full affiliate URL, raw IP, User-Agent, referrer URL or anonymous cookie is stored. No IP/UA hashes are collected because this foundation does not need them. The request logger redacts `/go` paths. Click logs contain IDs, destination host and status; query strings and secrets are omitted. Bounded Micrometer counters track requested, redirected, blocked and failed clicks without high-cardinality IDs.

HEAD returns 204 without lookup, redirect or click insertion. Obvious bot/crawler/spider/preview User-Agents and prefetch requests also return 204 without insertion. This is lightweight filtering, not fraud detection. Each ordinary successful GET records a raw navigation, including repeats; raw counts are not unique-user analytics. GET does not use POST-style idempotency keys or mutate user-owned state.

## HTTP and cache behavior

| Request/result | Status | Behavior |
| --- | --- | --- |
| Valid navigation | 302 | Location is the current approved affiliate URL; empty body |
| Unknown/malformed token | 404 | Safe PURCHASE_LINK_NOT_FOUND Problem Details |
| Expired/revoked/inactive/unsafe destination | 410 | Safe purchase error code; no Location |
| Click persistence failure | 503 | PURCHASE_TRACKING_FAILED; no Location |
| HEAD, obvious preview/bot, prefetch | 204 | No redirect or click |

Success and controlled error responses use `Cache-Control: no-store`. Existing security headers and `Referrer-Policy: no-referrer` remain in force. Purchase Problem Details uses `/go` as its instance rather than repeating a bearer token. No exception/SQL/destination details are exposed.

Cloudflare and any CDN/reverse proxy **must bypass edge caching for `/go/*`**, including overrides that otherwise cache everything. Route requests to the backend, honor no-store and redact token paths in edge/access logs. No Cloudflare configuration exists in this repository and none is deployed by this prompt.

## Migration and boundaries

V9 creates `purchase_link` and `affiliate_click`, adds programme host configuration and supporting composite identity keys, and enforces immutable click records and market-consistent attribution. V1–V8 remain unchanged. No production programme, offer, host or click data is seeded.

Affiliate domain/application classes remain framework-independent. JDBC and transaction management stay in infrastructure. Match API composition calls the affiliate application boundary; matching domain/application/infrastructure, analysis and compatibility cannot depend on affiliate/feed. No commercial data enters embedding descriptors or ranking. This preserves ADR-001 and ADR-017.

Tests exercise token shape/hash/reuse, ownership distinction, concurrency, destination injection and unsafe URLs, current URL updates, programme/market/offer shutdown, missing prices, HEAD/bots/replays, storage failure rollback, log redaction, history retention, clean migration and V8 upgrade checksums. No live affiliate network or credentials are used. Click-retention policy, coordinated key rotation, conversion attribution and commission processing remain future production work.
