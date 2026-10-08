# ADR-0005: PostgreSQL 17 + pgvector; Flyway owns the schema

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-SCN-06, FR-EXT-04, FR-ART-02, NFR-06, NFR-13

## Context

Both apps read the same data; RAG needs vector search; data must stay in CH/EU.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Managed Postgres in Frankfurt + pgvector | One database for relational, queue, full-text and vectors | Vector scale limited, fine for our volume |
| Postgres + dedicated vector DB | Specialised vector features | Extra cost and sync |

## Decision

PostgreSQL 17 with pgvector, managed, in an EU region (Frankfurt). Neon is the first candidate; plan and cost confirmed in the deploy spike. Flyway migrations in `apps/core` are the only way to change the schema; `apps/web` reads with Drizzle using introspected types and never migrates. Every tenant-owned table carries `account_id`.

## Consequences

Local dev and tests use `pgvector/pgvector:pg17` (docker-compose, Testcontainers).
