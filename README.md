# CoupleOutfit

The backend platform and Phase 0 frontend foundations are in place. No CoupleOutfit business features are implemented.

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

Expected production CORS configuration is `https://coupleoutfit.com,https://www.coupleoutfit.com`; supply it explicitly. Future OpenAI, R2, and affiliate credentials are not consumed or required.

Flyway migrations live in `apps/backend/src/main/resources/db/migration`. V1 enables pgvector only. Staging/production must provide pgvector binaries and an appropriately privileged migration connection. Flyway failure prevents startup. Hibernate validates and does not evolve schemas.

## Backend image

```bash
docker build -t coupleoutfit-backend:local apps/backend
```

Supply the active profile, reachable JDBC URL and credentials at runtime. Container `localhost` is not the host database. The image runs as a non-root user. Image packaging skips tests because Docker-based integration tests run separately in CI with `clean verify`.

See [backend architecture](docs/architecture/backend-foundation.md) and [ADRs](docs/adr/README.md). The approved baseline is Java 21 and Spring Boot 4.1.1 (Corrective Prompt 001A).
