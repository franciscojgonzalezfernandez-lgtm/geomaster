# ADR-0004: Postgres-backed job queue in the core service

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-SCN-05, NFR-02

## Context

Scans fan out into many idempotent units (prompt × engine × language × sample) that must survive restarts, retry safely and respect provider rate limits, within a ≤ CHF 130/month fixed budget.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Own queue table with SELECT … FOR UPDATE SKIP LOCKED | No extra infrastructure; full control of idempotency keys and rate limits; good engineering showcase | ~200 lines to own and test |
| JobRunr (Postgres storage) | Dashboard and retries out of the box | Version compatibility with Spring Boot 4 to verify; advanced features are paid |
| Redis + a broker | Mature | Another paid service to run |

## Decision

Own queue table in Postgres consumed with `FOR UPDATE SKIP LOCKED` by virtual-thread workers. Each unit has an idempotency key (project, prompt, engine, locale, sample, scan) and a per-provider rate limiter. Fallback: JobRunr, if the spike shows the own queue costs more than 2 days.

## Consequences

No Redis. Queue metrics exported via OpenTelemetry.
