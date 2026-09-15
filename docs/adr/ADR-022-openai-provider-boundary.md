# ADR-022 — OpenAI provider boundary

Status: Accepted for Prompt 005.

OpenAI is an infrastructure adapter behind ClothingAnalysisPort. The official Java SDK uses Responses image input and strict structured output; no SDK/HTTP types cross into application or domain. SourceImageAccessPort supplies verified private bytes or an already resolved trusted catalogue image reference.

The model and bounded timeout/retry limits are configuration. Network calls occur outside database transactions. Missing credentials disable analysis without making platform readiness depend on OpenAI. Tests use mocked SDK responses and test-only provider overrides; no production fake or live CI calls exist.
