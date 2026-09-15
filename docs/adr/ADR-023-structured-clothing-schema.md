# ADR-023 — Structured clothing schema

Status: Accepted for Prompt 005.

Provider output must validate into the versioned CoupleOutfit-owned ClothingAttributes schema before persistence. Initial schema version is `1`; prompt version is `clothing-analysis-v1`. Controlled vocabularies share the catalogue category enum, bound lists and permit uncertainty. Confidence is optional per score and bounded to [0,1]. Material fields describe appearance, not fibre composition.

Only clothing is analysed; sensitive human attributes and commercial market/affiliate data are excluded. Corrections use the same attribute validation and cannot edit system metadata. Schema/prompt versions remain attached to immutable revisions so future changes do not reinterpret history.
