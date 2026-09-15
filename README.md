# CoupleOutfit

The backend platform and Phase 0 frontend foundations are in place. Prompt 002 adds market and retailer foundations; matching and other product workflows are not implemented.

The canonical Next.js frontend location is `apps/frontend`; see the [frontend README](apps/frontend/README.md) for setup and validation.

## Prerequisites

- Java 21 (set `JAVA_HOME` if another JDK is your default)
- Docker with Compose, running and accessible to your user
- Internet access for the first Maven dependency and Docker image downloads

The root `.java-version` declares Java 21 for compatible version managers. Otherwise set `JAVA_HOME` to your JDK 21 installation and put `$JAVA_HOME/bin` first on `PATH`; confirm with `java -version`. Maven enforces Java 21. On this Homebrew development machine, use:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

Maven 3.9.11 is downloaded by the included Maven Wrapper 3.3.4; global Maven is unnecessary.

## Local development

From the repository root:

```bash
docker compose up -d postgres
cd apps/backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The local profile connects to the Compose PostgreSQL instance on localhost:5432 using clearly local-only credentials. Health endpoints on port 8080:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`

Run tests from `apps/backend` with Docker running:

```bash
./mvnw clean verify
```

Tests use an isolated pgvector PostgreSQL Testcontainer, never H2. Docker unavailability fails integration tests rather than silently skipping them.

Stop infrastructure from the repository root:

```bash
docker compose down
```

The database volume is retained. Removing a volume destroys local database data; ordinary shutdown does not remove it.

## Configuration

Copy `.env.example` to `.env` only if you need to change local defaults. Never commit `.env` or secrets. Compose reads root `.env`; a locally running Spring process does **not**. Export matching variables in your shell if you override credentials or ports. Changing `POSTGRES_PORT` also requires updating `DATABASE_URL`. PostgreSQL initialization credentials only apply to a new data volume.

| Variable | Meaning |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `local`, `staging`, or `production`; integration tests activate `test` |
| `DATABASE_URL` | PostgreSQL **JDBC** URL; mandatory outside local/tests |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | External database credentials; mandatory outside local/tests |
| `CORS_ALLOWED_ORIGINS` | Comma-separated explicit origins; local defaults to http://localhost:3000, otherwise none |
| `PORT` | Backend port, default 8080 |
| `POSTGRES_PORT` | Compose host port, default 5432 |

Expected production CORS configuration is `https://coupleoutfit.com,https://www.coupleoutfit.com`; supply it explicitly. OpenAI credentials are optional for explicitly enabled clothing analysis. R2 and affiliate credentials are not consumed or required.

Flyway migrations live in `apps/backend/src/main/resources/db/migration`. V1 enables pgvector; V2 creates and seeds the market reference foundation and creates empty retailer tables. Staging/production must provide pgvector binaries and an appropriately privileged migration connection. Flyway failure prevents startup. Hibernate validates and does not evolve schemas.

## Market and retailer foundation

`GET /api/v1/context` returns the configured active market; `GET /api/v1/retailers` initially returns an empty list. Backend launch defaults are centralized in application.yml. Deployment overrides use `MARKET_DEFAULT_COUNTRY_CODE`, `MARKET_DEFAULT_CURRENCY_CODE`, and `MARKET_DEFAULT_LOCALE_CODE`; invalid or inactive reference data fails startup. There is no public market switching. See [market/retailer architecture](docs/architecture/market-retailer-foundation.md).

## Backend image

```bash
docker build -t coupleoutfit-backend:local apps/backend
```

Supply the active profile, reachable JDBC URL and credentials at runtime. Container `localhost` is not the host database. The image runs as a non-root user. Image packaging skips tests because Docker-based integration tests run separately in CI with `clean verify`.

See [backend architecture](docs/architecture/backend-foundation.md) and [ADRs](docs/adr/README.md). The approved baseline is Java 21 and Spring Boot 4.1.1 (Corrective Prompt 001A).


### Local source image upload

With PostgreSQL running, start the backend using the `local` profile. Source files use ignored `apps/backend/.local/source-assets` when launched from `apps/backend`; override with `SOURCE_STORAGE_LOCAL_ROOT`. The Docker image provides writable `/app/.local/source-assets` for its non-root user. Production storage remains disabled until private R2 is configured.

Example using curl and jq, with a small local PNG:

```bash
curl -sS -c /tmp/coupleoutfit-cookies -H 'X-Source-Request: 1' -H 'Content-Type: application/json' \
  -d '{"sourceType":"IMAGE_UPLOAD"}' http://localhost:8080/api/v1/source-items > /tmp/coupleoutfit-source.json
SOURCE_ID=$(jq -r .sourceItemId /tmp/coupleoutfit-source.json)
ASSET_ID=$(jq -r .upload.assetId /tmp/coupleoutfit-source.json)
UPLOAD_URL=$(jq -r .upload.uploadUrl /tmp/coupleoutfit-source.json)
curl -f -X PUT -H 'Content-Type: image/png' --data-binary @your-image.png "http://localhost:8080$UPLOAD_URL"
curl -sS -b /tmp/coupleoutfit-cookies -H 'X-Source-Request: 1' -H 'Content-Type: application/json' \
  -d "{\"assetId\":\"$ASSET_ID\"}" "http://localhost:8080/api/v1/source-items/$SOURCE_ID/upload-complete"
curl -sS -b /tmp/coupleoutfit-cookies "http://localhost:8080/api/v1/source-items/$SOURCE_ID"
```

Expect READY_FOR_ANALYSIS; no AI call occurs. Upload authorization expires after 15 minutes and permits one write. The local upload/read endpoints do not exist in staging/production. Unsupported product URLs return IMAGE_UPLOAD_REQUIRED; POST the owned source's `/upload-authorization` endpoint to obtain fallback upload details. All source POSTs require `X-Source-Request: 1`.

For browser development, use same-origin routing and configure `SOURCE_PUBLIC_ORIGIN`; CORS credentials remain disabled. Local anonymous cookies use a process-generated signing key unless `SOURCE_SESSION_SIGNING_KEY` is supplied, so a restart normally invalidates prior cookies. Use a stable random development key for persistence across restarts. See [source input architecture](docs/architecture/source-input-foundation.md) for ownership, storage, validation and production prerequisites.


### Clothing analysis (Prompt 005)

Normal tests use mocked OpenAI responses and a test-only provider; `clean verify` does not call live OpenAI or require an API key. By default analysis is disabled, while the application and existing APIs remain healthy.

For an optional live smoke test, set `CLOTHING_ANALYSIS_PROVIDER=openai`, supply `OPENAI_API_KEY` through your local secret environment, and set `OPENAI_CLOTHING_ANALYSIS_MODEL` to a vision-capable Responses model supporting structured output. No model is selected by default. Never put credentials in source files or shell command arguments. Start the local backend and complete the source-upload workflow above with an image you intend to send to OpenAI, then:

```bash
curl -sS -b /tmp/coupleoutfit-cookies -H 'X-Source-Request: 1' -X POST \
  "http://localhost:8080/api/v1/source-items/$SOURCE_ID/analysis"
curl -sS -b /tmp/coupleoutfit-cookies \
  "http://localhost:8080/api/v1/source-items/$SOURCE_ID/analysis"
```

A successful response contains `COMPLETED` and AI revision 1. Repeating POST returns the existing result without another provider call. Without provider configuration it fails safely with `ANALYSIS_PROVIDER_UNAVAILABLE`. A correction POST to `/analysis/revisions` accepts `parentRevisionId` plus the full returned `attributes` object; stale parents return 409. See [clothing analysis architecture](docs/architecture/clothing-analysis-foundation.md) for schema, retry limits, privacy, pending production R2 integration and recovery limitations.


### Embeddings and internal candidate retrieval (Prompt 006)

Normal CI uses deterministic fake embeddings and mocked SDK responses; it never calls the live provider. Embeddings default to disabled. Enable explicitly with `EMBEDDING_PROVIDER=openai`, reuse `OPENAI_API_KEY` from your secret environment, and optionally set `OPENAI_EMBEDDING_MODEL` (default `text-embedding-3-small`). Storage and requested output are fixed at 1536 dimensions; changing the dimension requires a migration. No new public endpoint is added.

Internal `EnsureProductEmbedding.prepareBatch` prepares up to 50 existing products. `RetrieveCompatibilityCandidates` uses the owned SourceItem's current analysis and persisted market. Vector similarity only orders retrieval candidates; it is not Couple Match Score. See [embedding/retrieval architecture](docs/architecture/embedding-candidate-retrieval-foundation.md) for eligibility, versioning and recovery limits.
