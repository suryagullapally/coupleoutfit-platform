# ADR-034 — Commercial updates preserve fashion embeddings

Status: Accepted for Prompt 008.

Price, availability and URL changes belong to ProductOffer and do not change Product's fashion descriptor or source-version timestamp. Product/style changes advance that timestamp, allowing existing missing/stale embedding detection to exclude old vectors. Preparation is a separate bounded operation; feed ingestion never synchronously embeds each row or deletes vector history.

See [affiliate feed ingestion foundation](../architecture/affiliate-feed-ingestion-foundation.md).
