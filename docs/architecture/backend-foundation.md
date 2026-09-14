# Backend foundation

One Java 21 / Spring Boot 4.1.1 Maven deployable forms a modular monolith. PostgreSQL 17 is the system of record. pgvector 0.8.2 is installed through Flyway V1, with no embeddings. Prompt 002 adds the [market and retailer foundation](market-retailer-foundation.md) through V2. Flyway owns schema changes; Hibernate uses validation, and open-session-in-view is disabled.

## Boundaries

`com.coupleoutfit.CoupleOutfitApplication` bootstraps the application. Only needed cross-cutting packages exist: `shared.api`, `shared.observability`, and `shared.security`. Do not add placeholder classes to retain empty packages.

Future features use `api → application → domain`. Infrastructure implements application/domain ports. Domains must not depend on MVC, provider SDKs, JPA repository implementations or transport DTOs. ArchUnit now enforces the market/retailer domain and application boundaries; no generic CRUD base types are included. Feature concerns stay in their owning modules rather than accumulating in `shared`.

Planned boundaries: application, market, source, analysis, catalogue, retailer, affiliate, feed, compatibility, feedback, storage, jobs, shared. APIs use `/api/v1`; context and retailer read endpoints are documented in the market/retailer foundation.

## Architecture decisions

[ADR-001](../adr/ADR-001-uk-first-market-aware.md): UK-first, market-aware. GB/GBP/en-GB are future launch defaults, not reusable platform constants. Persist absolute timestamps using Java `Instant` and PostgreSQL `timestamptz`, with UUID IDs, snake_case names, explicit constraints and purposeful indexes. JDBC/Jackson and Compose database time settings use UTC. Market locale is independent of server timezone.

[ADR-017](../adr/ADR-017-outfit-compatibility-engine.md): The MVP is partner matching, but reusable compatibility/retrieval/ranking architecture must remain person-relationship-neutral so future product modes can reuse it without redesigning the engine.

Only market/retailer foundations exist; no matching or future modes are implemented. The public score remains Couple Match Score.

## API, security and observability

A highest-precedence filter validates inbound `X-Request-Id` against a 1–64 character ASCII allowlist or generates a UUID. It sets a request attribute, response header and MDC, restoring MDC after processing. Current endpoints are synchronous; revisit correlation propagation and completion logging when async request work is introduced.

MVC advice produces RFC 9457 Problem Details with stable `code` and `traceId`, sanitized validation/framework errors, and generic 500 details. Extend the advice for genuine domain errors later. Test-only probe controllers exercise failures without creating production business endpoints. Security-layer CORS rejections use the framework's safe 403 response.

Access logs include UTC timestamps, level, request ID, HTTP method, bounded path, status and elapsed milliseconds. They exclude query strings, bodies, cookies, authorization and exception messages. Staging/production use Boot's built-in ECS JSON logging. Do not place secrets in URL paths.

Security is explicitly stateless, with no account/authentication flow, generated default user, login page or HTTP Basic. Current endpoints permit anonymous access. CSRF is disabled because there are no ambient cookie/session credentials; reassess before adding anonymous-session cookies or authentication. CORS permits configured exact origins, allows no credentials and rejects wildcard origins. Security headers include nosniff, DENY framing, CSP, no-referrer, permissions policy and HSTS on HTTPS. Forwarded headers are not trusted by default; deployment must define a trusted proxy boundary and HTTPS/HSTS at its edge.

Only Actuator health is exposed. Details/components are hidden. Liveness excludes the database; readiness includes it. Other management endpoints have no web mapping. No infrastructure deployment or Cloudflare configuration is created.

## Development and verification

Primary workflow: local backend → Docker PostgreSQL/pgvector. A future Next.js frontend may run locally; its directory is reserved only. The backend multi-stage Java 21 image includes only the runtime JAR and runs non-root.

JUnit Jupiter tests use PostgreSQL Testcontainers, test context startup, migration history, extension installation, the exact approved schema, safe health exposure, validation, generic errors, correlation and CORS/headers. Docker is required, with no skipped-container fallback. GitHub Actions on PRs and pushes to main sets up Java 21, caches Maven and runs `./mvnw clean verify` on a Docker-capable Ubuntu runner. CI neither deploys nor publishes images.
