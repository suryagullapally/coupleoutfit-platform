# Match sessions and ranking — v1

Prompt 007 implements partner outfit matching. The public score is **Couple Match Score**. The reusable CompatibilityScorer, color policy and ranking strategies are relationship-neutral (ADR-017). No future modes, affiliate redirects, feedback, feeds or learning pipeline are implemented.

## Session lifecycle and ownership

A signed anonymous source-session cookie owns the SourceItem and, through it, every MatchSession. POST requires `X-Source-Request: 1`, the existing same-origin protections and a UUID `Idempotency-Key`. The key is hashed with the owner identity; it is never logged. Reusing a key with identical inputs returns the existing session, including after a source correction. Reusing it with different inputs returns 409. A new key creates a new session.

Creation validates source readiness and completed analysis, and requires the submitted revision to be current. It locks the source and analysis rows for this short transaction. The session pins the exact immutable AnalysisRevision, source market, selected ProductTarget, mode, same-retailer flag, algorithm version and ranking limits. No current market lookup or target inference happens during scoring or historical reads.

`CREATED → MATCHING → COMPLETED` is synchronous and bounded. An atomic state claim permits only one execution. Concurrent duplicate requests return the current state (202 while MATCHING). Provider work and candidate retrieval run outside the creation/finalization transactions. Final ordered results and COMPLETED status commit together. Zero visible results means COMPLETED with `results: []`. Retrieval, scoring or persistence failure produces FAILED with a safe fixed failure code and no partial result set. A failed or completed session is never rerun in place.

A process crash can leave a MATCHING session stranded. There is no background recovery or retention job in this prompt; operational recovery and anonymous-data retention remain production-hardening work. A caller may explicitly create a new session with a new key after a failure. Provider timeouts/retry bounds remain those of the existing embedding foundation.

## Market and retailer restrictions

ADR-001: the market is copied from SourceItem into the session, not supplied by the public request or recalculated from future default configuration. The launch remains GB / GBP / en-GB through existing reference data/configuration; ranking contains no launch-country constants.

Same-retailer means **the exact resolved RetailerMarket UUID in the persisted market**. Retailer identity, brand, domain and country alone are insufficient. Uploaded images and unsupported-URL fallback do not supply an eligible retailer identity, even if an unsupported URL recognized a host. Selecting the restriction without resolved catalogue provenance returns SAME_RETAILER_NOT_AVAILABLE (422).

CompatibilityRequest carries an optional retailer-market restriction. Existing pgvector SQL applies it before offer selection and the candidate LIMIT, alongside active reference/retailer/catalogue/matching flags, market, target, category, current embedding and availability eligibility. A perfect-scoring offer from another country cannot enter the candidate set. IN_STOCK and UNKNOWN remain eligible; OUT_OF_STOCK does not. Priced offers require the context currency; unpriced offers remain eligible.

## Algorithm and exact v1 weights

`RankingAlgorithm.VERSION = couple-match-v1` versions all component rules, weights, rounding, ordering and explanation templates. Weights are immutable code, not mutable database configuration. Any scoring or explanation-rule change must introduce a new algorithm version. MatchSession and each MatchResult persist this value.

| Component | EXACT | COORDINATED | COMPLEMENTARY |
|---|---:|---:|---:|
| Semantic | 25 | 15 | 10 |
| Color | 25 | 25 | 30 |
| Style | 15 | 20 | 20 |
| Formality | 15 | 20 | 20 |
| Pattern | 10 | 10 | 10 |
| Seasonality | 5 | 5 | 5 |
| Category | 5 | 5 | 5 |
| Total | 100 | 100 | 100 |

Each component is an integer 0–100. Final score = `HALF_UP(sum(component × weight) / 100)`. Validated components and weights guarantee a 0–100 result; there are no additional multipliers or hidden mode adjustments. A separate modeScore is omitted: mode effects are represented by color/pattern rules and weights.

EXACT rewards close stylistic resemblance, not identical SKU identity. COORDINATED (the default) rewards harmonious colors and balanced patterns without requiring identical styling. COMPLEMENTARY rewards controlled color contrast while retaining style, formality and seasonal compatibility; it does not invert similarity.

### Components

- **Semantic:** finite cosine similarity clamped to 0–1, multiplied by 100, rounded HALF_UP. Semantic similarity is one input, never the final score.
- **Style:** retain canonical known style tokens. Missing/unknown-only gives 40. Exact overlap uses rounded Jaccard percentage (`100 × intersection / union`). Without overlap, a shared family gives 75; otherwise 30. Families: CLASSIC/MINIMAL/ELEGANT/TAILORED/PREPPY/CONTEMPORARY; SPORTY/STREETWEAR/UTILITY/RELAXED; ROMANTIC/BOHEMIAN/VINTAGE; ELEGANT/EVENING/FESTIVE. Partial overlap can score below a compatible-family comparison; v1 deliberately preserves the transparent overlap calculation.
- **Formality:** SPORTY=0, CASUAL=1, SMART_CASUAL=2, SMART=3, FORMAL=4, EVENING=4. Score `max(0,100 − 25 × absolute level difference)`. Missing, unknown or ambiguous multiple values give 40.
- **Pattern:** one known token each is required, otherwise 40. Identical patterns give 100/90/75 for Exact/Coordinated/Complementary. One solid and one patterned gives 65/95/95. Different non-solid patterns give 25/45/45. Known patterns use the existing SOLID, STRIPED, CHECKED, FLORAL, POLKA_DOT, ANIMAL, PRINTED, TEXTURED, COLOUR_BLOCK vocabulary.
- **Seasonality:** shared known season gives 100; disjoint known seasons 25; missing/unknown gives 40. No weather or current-date inference.
- **Category:** accessory pairings 0 (also excluded upstream), OTHER 40, same category 100, differing footwear pairing 60, other eligible differing garment categories 85. These are broad partner-outfit relationships, not outfit-completion rules.

### Color table

Canonical families: BLUE/NAVY/LIGHT_BLUE; RED/BURGUNDY/PINK; GREEN/OLIVE; YELLOW/GOLD; WHITE/CREAM; GREY/SILVER; BROWN/BEIGE; singleton PURPLE, ORANGE, BLACK. Neutral tokens are BLACK, WHITE, GREY, SILVER, NAVY, BROWN, BEIGE, CREAM. Complementary family pairs are BLUE–ORANGE, RED–GREEN and PURPLE–YELLOW.

Rules apply in the order below; the first matching rule wins:

| Relationship | EXACT | COORDINATED | COMPLEMENTARY |
|---|---:|---:|---:|
| Same token | 100 | 88 | 55 |
| Same family | 90 | 85 | 60 |
| Complementary families | 30 | 80 | 100 |
| Either token neutral | 60 | 95 | 85 |
| Other known relationship | 35 | 55 | 45 |

Primary and secondary colors are combined, sorted and deduplicated. For each known source color choose its best candidate-color relationship, then round the mean across source colors. Missing known colors on either side give 40. UNKNOWN and MULTICOLOUR provide no inferred relationship. This is a small heuristic table, not a calibrated color-science model; the directional best-match aggregation can under-penalize additional candidate colors.

## Ranking, bands and explanations

Configuration defaults: `coupleoutfit.matching.candidate-limit=50`, `result-limit=12`, `minimum-score=60`. Validation permits candidates 1–200, results 1–50 and at most candidate count, minimum 60–100. Limits are pinned to the session. Only final visible results are persisted.

Order: final score descending, raw semantic similarity descending, IN_STOCK before UNKNOWN, ProductOffer UUID ascending (canonical textual order, equivalent to unsigned UUID order). No randomness, wall clock, AI, money, brand or retailer bonus enters the scoring function. Time is used only for lifecycle timestamps. A pre-ranking limit necessarily limits recall.

Bands: 90–100 EXCELLENT; 80–89 GREAT; 70–79 GOOD; 60–69 POSSIBLE; below 60 BELOW_THRESHOLD and hidden. A configured higher minimum may hide additional results.

Explanations are persisted structured JSON `{ "reasons": [...] }`, never HTML. Select strongest positive components descending; ties prefer color, style, formality, pattern, seasonality, semantic. Color/style/formality/seasonality require at least 75, pattern at least 65, semantic at least 85. Color and pattern templates are mode-aware. Maximum three reasons, two where enough signals exist, one when only one honest positive exists. No known positive means no fabricated reason. Category is not presented as a reason. Material is not a scoring component and no material claim is generated. Templates contain no vector or coefficient terminology.

## Persistence and historical guarantees

V7 adds only match_session and match_result. Foreign keys reference SourceItem, AnalysisRevision, canonical market data, RetailerMarket and ProductOffer. SQL guards validate source/revision/market relationships and prevent configuration/final-state or result updates. Results enforce rank/offer uniqueness per session, score bounds and band consistency. Application finalization is atomic. The source index supports ownership joins; unique keys provide the session-result access indexes.

MatchResult persists offer identity, rank, final score/band, all seven components, structured explanation, algorithm version and UTC Instant. Historical reads do not rescore. Source revision, result identity/order, scores, components, explanation and algorithm are preserved. Current product/retailer/offer display data can change: v1 does not snapshot the complete catalogue or promise full later recomputation from changed candidate attributes. Existing reference FKs prevent deleting referenced offers/revisions accidentally. No public component/vector diagnostics endpoint exists.

A small JDBC adapter implements meaningful matching repository operations. It joins existing analysis/catalogue tables for pinned revisions and response display; it does not access other modules' infrastructure classes or duplicate candidate-retrieval SQL. This is a deliberate modular-monolith persistence compromise avoiding duplicate entity mappings for read projections. Cross-module use cases remain application/domain dependencies.

## API

POST `/api/v1/match-sessions` accepts sourceItemId, analysisRevisionId, partnerTarget, optional matchMode and optional sameRetailerOnly. It requires the owner cookie, `X-Source-Request: 1` and UUID `Idempotency-Key`. Unknown fields (including caller-selected market) are rejected. Returns 200 with the current/full session or 202 while another request is executing it. GET `/api/v1/match-sessions/{id}` returns the owned persisted session. Missing/non-owned/invalid-owner access uses the same 404; mutable-state conflicts use 409; unavailable same-retailer uses 422. Failures during execution return the persisted FAILED state and safe failureCode.

Responses include market/configuration, source/revision IDs, algorithm version and safe results: score, band, reasons, product id/name/brand/category, retailer id/slug/name, offer id, nullable amountMinor/currencyCode, availability and imageUrl. No raw affiliate URL, vector, owner token or internal component scores. Missing price is represented by two nulls, never a fabricated zero. Responses use no-store and existing correlation/security headers.

The signed owner cookie is now scoped to `/api/v1`; new source creation and owned source GET reissue the same token at that path and expire its old `/api/v1/source-items` path. This preserves ownership without rotating identity. Existing clients holding only the old path must GET their source once before calling match APIs. HttpOnly, SameSite=Strict, Secure in production and expiry/signature validation remain intact. There is no new authentication system.

## Validation and limits

Deterministic unit fixtures cover component rules, unknowns, exact bands/threshold, rounding, ties, casual/smart/formal/monochrome/patterned outfits, and three-mode golden ranking differences. PostgreSQL integration tests cover the real embedding/retrieval/session/result pipeline with fake providers, market and same-retailer isolation, missing price/commercial independence, corrections/history, concurrency, failures, migrations and ownership. No live OpenAI call is needed.

These initial heuristics are explainable regression baselines, not a claim of human-validated fashion quality. Calibration with curated human-reviewed examples is future work. No price, affiliate configuration, commission or brand incentive can affect v1 fashion scoring.
