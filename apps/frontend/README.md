# CoupleOutfit frontend

Canonical location: `apps/frontend`. Next.js 16 App Router, React, strict TypeScript, Tailwind CSS 4 and ESLint. npm is the only frontend package manager; the lockfile pins installs. The public homepage creates SourceItems from product URLs or private image uploads, and the owned review route analyses the ready source and supports immutable corrections.

## Run and validate

Use Node.js 22.18 or newer (Node 22 LTS recommended) and npm. From this directory:

```bash
npm ci
npm run dev
npm run lint
npm run typecheck
npm test
npm run build
npm start
```

Development and production servers use localhost:3000; stop the development server before `npm start`. Source input requires the backend and PostgreSQL. The Next.js rewrite keeps `/api/*` and local `/internal/*` upload requests same-origin so the backend's HttpOnly ownership cookie remains browser-managed. Run the backend with `SOURCE_PUBLIC_ORIGIN=http://localhost:3000`; do not enable credentialed cross-origin requests. `typecheck` generates Next route types before checking TypeScript. Unit tests use Node's built-in test runner and native TypeScript stripping, with no test framework dependency.

## Environment

Optionally copy `.env.example` to `.env.local`. Both settings are public and are embedded in browser bundles at build time. Rebuild when deployment values change. Never add secrets, OpenAI keys, affiliate credentials or database credentials here.

- `NEXT_PUBLIC_API_BASE_URL`: server-side rewrite destination for the backend, default `http://localhost:8080`; browser SourceItem calls remain on the frontend origin.
- `NEXT_PUBLIC_SITE_URL`: metadata base URL, default `http://localhost:3000`; set the deployed site URL when hosting.

Root metadata uses `index, follow`, making `/` and the minimal public legal route shells indexable. The analysis review route `/match?source={sourceItemId}` overrides metadata to `noindex, follow`. No match-session result route exists yet.

## Structure and boundaries

- `app/`: server-rendered pages and Metadata API.
- `components/`: shared public shell, PageContainer and isolated client MarketProvider.
- `features/home/`: homepage sections and copy; only the source-entry island is a Client Component.
- `features/source/`: exact SourceItem transport DTOs, runtime response checks, input validation and image-upload orchestration.
- `features/analysis/`: exact clothing-analysis DTOs, controlled vocabulary, bounded status recovery, review and immutable correction UI.
- `features/matching/`: partner preferences, exact MatchSession transport, idempotent creation and bounded processing recovery.
- `config/`: public environment settings and the single approved default market.
- `types/`: market and generic HTTP transport types, no invented domain DTOs.
- `styles/`: semantic colors, typography, spacing, content width, radii and keyboard focus conventions through Tailwind's CSS theme.
- `lib/`: money formatting and generic API transport.
- `tests/`: money and HTTP transport unit tests using in-memory fetch stubs only.

Server Components are the default. The root layout, page and presentation sections stay on the server. MarketProvider accepts server-rendered children, and the source-entry island uses client rendering only for the native file selection state. Server Components can read central market configuration; future client islands can use `useMarket()`. No market switching UI or API exists.

[ADR-001](../../docs/adr/ADR-001-uk-first-market-aware.md): the approved launch market is defined only in `config/market.ts`. Reusable components consume market data. `formatMoney(amountMinor, currencyCode, locale)` uses Intl currency precision (including zero- and three-digit currencies), supports negative amounts, and throws RangeError for invalid inputs instead of guessing a price. Catch errors at a future feature boundary and show an appropriate unavailable state; do not display exception stacks.

[ADR-017](../../docs/adr/ADR-017-outfit-compatibility-engine.md): generic infrastructure remains neutral. The homepage explains the approved partner-matching product, but it adds no matching flow or future product mode.

## SourceItem API integration

The source API calls `POST /api/v1/source-items`, `POST /api/v1/source-items/{id}/upload-authorization`, the authorized upload URL, `POST /api/v1/source-items/{id}/upload-complete`, and the owned status GET. Source API calls use `credentials: same-origin`; the signed `coupleoutfit_session` cookie remains HttpOnly and is never copied into JavaScript or browser storage. Other generic API clients still default to omitted credentials.

Product URL resolution and image completion are synchronous in the current backend. The UI therefore does not poll or invent processing percentages. Unsupported URLs keep the same SourceItem and request its image-upload authorization. Upload authorization values remain in component memory only. Image object URLs are revoked on replacement and unmount.

Responses include typed `data`, HTTP `status`, and a safe `requestId` when exposed. Empty responses use undefined data. Generic types document the caller's expected response; they do not validate runtime business payloads. Add contract-specific validation when real contracts exist.

Errors distinguish HTTP (`ApiError`), network (`NetworkError`) and invalid successful JSON (`ApiResponseError`) failures. HTTP errors retain only status, safe code and request ID from existing Problem Details; raw bodies and server messages are discarded. Source-specific presentation maps stable error codes to customer language. State-changing creation is never automatically retried. Safe upload continuation retains only the SourceItem/asset identifiers and whether bytes reached storage, in component memory. No authentication, analytics or later matching requests are implemented.

## Clothing analysis and review

Once a SourceItem reaches `READY_FOR_ANALYSIS`, the homepage continues to `/match?source={sourceItemId}`. The route retrieves the owned source and any existing analysis before offering `POST /api/v1/source-items/{sourceItemId}/analysis`; this keeps analysis user initiated while preserving the backend's safe repeated-start contract. Existing `PENDING` and `IN_PROGRESS` analyses use bounded, abortable GET polling only as recovery for concurrent or refreshed sessions. `COMPLETED` shows the current revision and `FAILED` shows customer-safe recovery guidance.

Corrections send the backend's complete controlled `attributes` object plus `parentRevisionId` to `POST /api/v1/source-items/{sourceItemId}/analysis/revisions`. A revision conflict reloads the authoritative latest revision rather than overwriting it. Confidence and revision metadata remain read-only. The final confirmation is currently a local UI boundary because the backend exposes no analysis-confirmation endpoint; Prompt 004 stops before partner preferences or MatchSession creation.

Analysis calls use the same same-origin rewrite and HttpOnly `coupleoutfit_session` ownership cookie as SourceItem calls. The source identifier in the query string selects an owner-protected resource; no cookie, upload authorization or provider credential enters the URL or browser storage.

## Partner preferences and MatchSession creation

A completed current analysis revision can continue to the compact partner-preference step. Refreshing `/match?source={sourceItemId}` reconstructs eligibility through owned SourceItem and analysis GET requests; the local review confirmation is only a UI transition. The form uses the backend's `MENSWEAR` and `WOMENSWEAR` targets, `EXACT`, `COORDINATED` and `COMPLEMENTARY` modes, and `sameRetailerOnly`. Coordinated is the UI default and same-retailer matching defaults off.

Creation calls `POST /api/v1/match-sessions` with the source and current revision identifiers, preferences, `X-Source-Request: 1` and a UUID `Idempotency-Key`. The browser sends no market fields because the backend resolves and freezes the authoritative MarketContext. An unchanged retry reuses its key while a changed preference set receives a new key; automatic POST retries are never performed.

Normal matching completes synchronously. The frontend also handles the backend's persisted `CREATED` and `MATCHING` states with bounded, abortable GET polling. `COMPLETED`, including zero-result completion, navigates to `/match/{matchSessionId}`. That `noindex, follow` route reloads the owned MatchSession from the backend and renders its authoritative ranked results.

Completed result DTOs are runtime-validated for rank, score, backend score band, explanation reasons, product and retailer identity, optional minor-unit price/currency, availability, optional HTTP(S) image URL, and the purchase contract. Array order is rendered unchanged. Scores and reasons are never generated or adjusted in the browser, and candidates below the backend's public threshold are rejected as a contract violation rather than resurrected.

Offer prices use the shared `formatMoney` helper with the MatchSession locale and offer currency. Missing prices, brands, images, reasons and purchase links have explicit unavailable states. Product images come from backend-approved feed data; because current hosts are data-dependent and no image proxy or host allowlist contract exists, the route uses native responsive images with fixed aspect ratios, no referrer, eager loading only for the first result and lazy loading thereafter.

The browser-facing purchase URL is the exact backend-issued `/go/{token}` path. A narrow Next.js rewrite forwards `/go/:token` to the backend; a normal anchor performs the GET, and the backend records the affiliate click before its guarded external redirect. The frontend neither resolves the destination nor emits a duplicate click request. Results reuse the approved commission disclosure.

## Tooling compatibility

ESLint is pinned to 9.39.5 because the React/import/accessibility plugins currently supplied by `eslint-config-next` do not support ESLint 10. npm marks ESLint 9 deprecated; revisit this pin when the upstream plugin set supports ESLint 10. No peer-dependency bypass or rule disabling is used.
