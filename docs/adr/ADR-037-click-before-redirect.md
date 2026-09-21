# ADR-037 — Persist clicks before redirect

Status: Accepted for Prompt 009.

Validate and insert AffiliateClick in a short transaction before returning HTTP 302. Storage failure returns 503 without redirect, preserving tracking integrity. HEAD and obvious preview requests do not insert clicks; ordinary repeated navigations remain distinct raw clicks. No external call occurs inside the transaction.

See [purchase-link architecture](../architecture/affiliate-click-and-purchase-link-foundation.md).
