# CoupleOutfit frontend — Phase 0

Canonical location: `apps/frontend`. Next.js 16 App Router, React, strict TypeScript, Tailwind CSS 4 and ESLint. npm is the only frontend package manager; the lockfile pins installs. No business APIs or features exist yet.

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

Development and production servers use localhost:3000; stop the development server before `npm start`. The homepage makes no API calls and does not require the backend or Docker. `typecheck` generates Next route types before checking TypeScript. Unit tests use Node's built-in test runner and native TypeScript stripping, with no test framework dependency.

## Environment

Optionally copy `.env.example` to `.env.local`. Both settings are public and are embedded in browser bundles at build time. Rebuild when deployment values change. Never add secrets, OpenAI keys, affiliate credentials or database credentials here.

- `NEXT_PUBLIC_API_BASE_URL`: backend base URL, default `http://localhost:8080`; a path prefix is supported.
- `NEXT_PUBLIC_SITE_URL`: metadata base URL, default `http://localhost:3000`; set the deployed site URL when hosting.

Root metadata uses `index, follow`, making `/` indexable. Future legal pages inherit normal indexing; future `/match` and `/match/[matchSessionId]` routes must override metadata to `noindex, follow`. These routes are not implemented in Phase 0.

## Structure and boundaries

- `app/`: server-rendered layout and foundation page, Metadata API.
- `components/`: PageContainer and an isolated client MarketProvider.
- `features/`: reserved, empty; future feature code belongs here.
- `config/`: public environment settings and the single approved default market.
- `types/`: market and generic HTTP transport types, no invented domain DTOs.
- `styles/`: semantic colors, typography, spacing, content width, radii and keyboard focus conventions through Tailwind's CSS theme.
- `lib/`: money formatting and generic API transport.
- `tests/`: money and HTTP transport unit tests using in-memory fetch stubs only.

Server Components are the default. The root layout and page stay on the server. MarketProvider is the only client boundary and accepts server-rendered children; it does not turn the page into a Client Component. Server Components can read central market configuration; future client islands can use `useMarket()`. No market switching UI or API exists.

[ADR-001](../../docs/adr/ADR-001-uk-first-market-aware.md): the approved launch market is defined only in `config/market.ts`. Reusable components consume market data. `formatMoney(amountMinor, currencyCode, locale)` uses Intl currency precision (including zero- and three-digit currencies), supports negative amounts, and throws RangeError for invalid inputs instead of guessing a price. Catch errors at a future feature boundary and show an appropriate unavailable state; do not display exception stacks.

[ADR-017](../../docs/adr/ADR-017-outfit-compatibility-engine.md): generic infrastructure remains neutral. There is no compatibility engine, matching flow, future product mode or business model in Phase 0.

## Future API integration

Construct `createApiClient(environment.apiBaseUrl)` only where a real backend contract is implemented. The client accepts relative paths under the configured base, JSON bodies, fetch options including AbortSignal, and optional headers. It uses native fetch with credentials omitted. Future cookie/session security requires an explicit coordinated change.

Responses include typed `data`, HTTP `status`, and a safe `requestId` when exposed. Empty responses use undefined data. Generic types document the caller's expected response; they do not validate runtime business payloads. Add contract-specific validation when real contracts exist.

Errors distinguish HTTP (`ApiError`), network (`NetworkError`) and invalid successful JSON (`ApiResponseError`) failures. HTTP errors retain only status, safe code and request ID from existing Problem Details; raw bodies and server messages are discarded. Render the safe error message, never `.stack`. Caller cancellation is propagated. No automatic retries, authentication, analytics, fake routes or domain requests are implemented.

## Tooling compatibility

ESLint is pinned to 9.39.5 because the React/import/accessibility plugins currently supplied by `eslint-config-next` do not support ESLint 10. npm marks ESLint 9 deprecated; revisit this pin when the upstream plugin set supports ESLint 10. No peer-dependency bypass or rule disabling is used.
