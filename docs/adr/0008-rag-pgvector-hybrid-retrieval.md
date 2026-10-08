# ADR-0008: RAG with pgvector and hybrid retrieval

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-EXT-04, FR-ART-02, FR-REC-01

## Context

Gap analysis and artifacts must be grounded in the client's site and in the pages AI engines cite, in four languages.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| pgvector + Postgres full-text, fused (RRF) | Hybrid search in one DB; language-aware full-text | Tuning needed |
| Vector-only | Simpler | Weaker on names, prices and exact terms |

## Decision

Pages are crawled, cleaned and chunked per section, embedded with a multilingual embedding model behind the provider layer, stored in pgvector with a per-language `tsvector`. Retrieval fuses vector and full-text results with Reciprocal Rank Fusion, filtered by project and language. The embedding model is chosen in the RAG spec with a small retrieval eval.

## Consequences

Index refreshed on each scan for cited pages and weekly for the client site.
