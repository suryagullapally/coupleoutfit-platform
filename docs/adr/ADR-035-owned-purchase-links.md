# ADR-035 — CoupleOutfit-owned purchase links

Status: Accepted for Prompt 009.

Owner-authorized match responses expose a CoupleOutfit `/go/{token}` URL or an explicit unavailable purchase state, never raw affiliate URLs. Link eligibility is composed at the API boundary and does not alter fashion ranking. No product-URL fallback is used.

See [purchase-link architecture](../architecture/affiliate-click-and-purchase-link-foundation.md).
