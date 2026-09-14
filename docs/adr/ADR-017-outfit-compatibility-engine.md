# ADR-017 — Outfit Compatibility Engine

Status: Accepted (approved Prompt 001).

## Decision

MVP v1 remains partner outfit matching. Reusable compatibility, candidate retrieval, and ranking internals must not assume that matching necessarily occurs between two people. The future reusable engine boundary is `compatibility`. Product-specific partner language and the public Couple Match Score remain valid.

## Future context

Possible future product modes may reuse the same engine.

## Explicit non-scope

Do not implement any future mode in MVP v1.0. Do not create speculative implementations. Prompt 001 does not implement the compatibility engine or any business functionality.
