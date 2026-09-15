# Clothing analysis foundation — Prompt 005

## Aggregate and revisions

`ClothingAnalysis` belongs to one owned `SourceItem`, independent of catalogue provenance. V5 enforces one aggregate per source. Only `READY_FOR_ANALYSIS` sources may start. A short transaction locks the source row, creates the aggregate and transitions `PENDING → IN_PROGRESS`. The application then reads the image and calls the provider **outside a database transaction**. A second short transaction inserts AI revision 1 and marks the aggregate `COMPLETED`; provider failure records `FAILED` with a safe classification.

Repeated starts return the existing state, including the current corrected revision. Concurrent starts cannot initiate another provider call. Failed starts do not implicitly retry on another click; automatic retries are bounded within the original execution. `startedAt` allows detecting interrupted executions. A process crash can leave `IN_PROGRESS`; recovery will require a separately designed, fenced application operation after a stale threshold. This prompt introduces no scheduler or automatic claim takeover.

Revisions are immutable Java records and Hibernate `@Immutable` persistence records. A database trigger rejects updates. Sequential numbering is unique within an aggregate; composite foreign keys keep parent/current revisions in the same aggregate. Corrections lock the aggregate and require the supplied parent to equal the current revision. They append `USER_CORRECTED` revisions, clear AI metadata/confidence on the new revision and never alter the AI revision or invoke OpenAI. A stale parent returns 409 rather than branching history. There is no public history or administration API.

## Schema and prompt

The CoupleOutfit-owned JSON schema is `analysis/clothing-analysis-schema-v1.json`, version `1`. The prompt is `analysis/clothing-analysis-v1.txt`, identified by `clothing-analysis-v1`. Every AI revision retains the configured model, provider `OPENAI`, request reference when available and prompt version. Corrections retain schema version but no provider metadata.

Attributes: category (shared `ProductCategory`), nullable subcategory, primary/secondary colors, pattern, material appearance, fit, formality, seasonality, style tags and visible details. All lists are immutable, bounded, controlled token sets without duplicates; primary/secondary colors are disjoint. Confidence covers category, primary colors and material appearance; each score may be null, otherwise it must be finite and in [0,1]. Low confidence is valid. Unknown scalar values use the available `UNKNOWN`, `OTHER` or null representation; uncertain lists may be empty.

The schema describes the dominant clothing subject, including outfit influence through tags/details. It does not decompose an outfit into garment aggregates. Material appearance does not claim fibre composition. Prompt and schema prohibit sensitive human attributes, gender inference, body measurements and attractiveness. Commercial market, price, affiliate and tracking metadata are absent from model input.

Provider structured output is validated again locally: exact required keys, JSON types, enum/token values, bounds and domain constructors. Malformed JSON, prose, missing fields, unknown fields and uncontrolled values are rejected. Corrections require the full attribute object and reject system metadata. They are not partial JSON patches.

## Provider and source image boundaries

`ClothingAnalysisPort` exposes only application/domain values. `OpenAiClothingAnalysisAdapter` contains all official OpenAI Java SDK 4.63.1 types. The SDK works with Java 21 and Spring Boot 4.1.1; its Jackson 2 types remain separate from Boot's Jackson 3 application mapper. There is no generic multi-provider framework.

The adapter uses the Responses API with image input, `store=false`, a strict JSON-schema text format, bounded output and an explicitly configured model. No model is selected by default. SDK automatic retries, redirects and SDK logging are disabled. Provider response bodies are neither persisted nor logged. Only validated attributes/confidence and safe metadata cross the boundary. `store=false` is a request storage setting, not a claim about all provider-side retention policies.

`SourceImageAccessPort` isolates image access. Verified uploads are read through the existing private storage port, bounded to the upload limit, and checked against recorded media type, byte size and SHA-256. Bytes are encoded in memory as a data URI for OpenAI; no public image route or permanent URL is introduced. A URL source may use only its persisted resolved image reference from a trusted catalogue offer. The backend performs no arbitrary remote fetch, scraping or URL resolution in this feature.

R2 remains a production prerequisite from Prompt 004. Production storage is still disabled until that adapter exists. The bytes-based boundary can work with a future private storage implementation; temporary signed R2 access can be added behind this boundary if approved. Analysis tables never contain images. Retention/deletion policy remains pending.

Official API references: [Responses Java API](https://developers.openai.com/api/reference/java/resources/responses/methods/create), [image input](https://developers.openai.com/api/docs/guides/images-vision), [structured output](https://developers.openai.com/api/docs/guides/structured-outputs).

## Configuration, failures and observability

Configuration lives under `coupleoutfit.ai.clothing-analysis`:

| Property | Default / bound |
| --- | --- |
| provider | `disabled`; explicitly use `openai` |
| api-key | `OPENAI_API_KEY`, empty by default |
| model | `OPENAI_CLOTHING_ANALYSIS_MODEL`, empty by default |
| connect-timeout | 5 seconds; positive, at most 10 |
| request-timeout | 20 seconds; positive, at most 30 |
| max-attempts | 2; configurable 1–3 |
| retry-backoff | 250 ms times retry number; base at most 2 seconds |
| image-detail | `low`; `high`/`auto` supported |
| max-output-tokens | 2048; 256–4096 |

Enable with `CLOTHING_ANALYSIS_PROVIDER=openai` and an explicitly chosen vision-capable, structured-output-capable Responses model. Missing credentials/model return `ANALYSIS_PROVIDER_UNAVAILABLE` when analysis is requested; startup and readiness remain healthy. Invalid limit configuration fails clearly. Normal CI requires no OpenAI key and uses only mocks/test overrides.

Timeout, network errors, provider 429/408 and 5xx are transient. Refusal, unsupported image, authentication/configuration failures and malformed structured output are permanent. Automatic retries happen only for transient failures and never exceed the configured attempt count. Default provider request budget is approximately 40 seconds plus backoff; the maximum configured provider budget is 96 seconds (three 30-second requests and two bounded backoffs). Storage and short database work are additional. Failure responses are safe Problem Details, usually 503; image rejection is 422, invalid corrections 400, source/parent conflicts 409 and non-owned/missing resources 404.

Micrometer records `analysis.request.count`, `analysis.success.count`, `analysis.failure.count`, `analysis.duration` and `analysis.retry.count`. Request counts represent claimed executions, not idempotent status reads. Only safe failure codes tag failures; no source/session identifiers tag metrics. Logs contain safe identifiers/status/error codes, never image bytes, signed URLs, prompts, raw provider output or secrets. Existing correlation, security headers, Actuator protection and edge rate-limit architecture remain intact. No new distributed rate limiter or metrics platform is introduced.

## API

All routes use the existing anonymous source cookie. Writes also require `X-Source-Request: 1` and existing same-origin checks. All analysis responses are `Cache-Control: no-store`.

- `POST /api/v1/source-items/{id}/analysis`: synchronously create/complete, or return existing status/current revision.
- `GET /api/v1/source-items/{id}/analysis`: current state, revision and editable attributes/confidence only.
- `POST /api/v1/source-items/{id}/analysis/revisions`: `{ "parentRevisionId": "...", "attributes": { ...full schema... } }`; append and return the new current revision.

Response fields are `id`, `status`, `currentRevision`, `failureCode`. Current revision fields are `id`, `revisionNumber`, `revisionType`, `schemaVersion`, `attributes`, `confidence`. No provider metadata, image access or full history is public.

## Architecture and scope

Pure domain/application packages depend on ports, not Spring, JPA, SDK or implementation packages. Infrastructure owns persistence mapping, provider client, image access and Micrometer. Controllers return explicit DTOs and reuse the source session API boundary without adding another ownership system. ArchUnit enforces these directions.

ADR-001: source market context is retained unchanged; no UK constants or market selection logic exist in analysis. ADR-017: clothing analysis remains relationship-neutral. No embeddings, retrieval, matching, score, feed, affiliate redirect, feedback or frontend changes are implemented.

Tests cover real PostgreSQL V5 clean/upgrade paths with unchanged V1–V4 checksums, JSONB round trips, immutability/FKs, ownership, corrections, concurrent starts/corrections, safe failures, private image access and synthetic SDK request/response mapping. Live OpenAI is an optional manual smoke test, never part of Maven verify.
