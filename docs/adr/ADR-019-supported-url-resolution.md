# ADR-019 — Supported URL resolution

Status: Accepted (approved Prompt 004).

Resolve only through explicit supported resolver boundaries. Persisted active catalogue offers may supply image metadata without fetching retailer pages. Unsupported, ambiguous or image-less URLs require image-upload fallback. Arbitrary scraping and remote-image proxying are prohibited. Any future outbound resolver must enforce DNS/connection/redirect SSRF protections.
