# ADR-0006: Hosting: Vercel for web, Fly.io for core, both in Frankfurt

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: NFR-03, NFR-06, budget

## Context

Fixed infrastructure must stay around CHF 100/month. The owner already pays Vercel (~USD 21/month).

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Vercel (fra1) + Fly.io (fra) | Web cost already covered; Docker deploy for Java; EU regions | Two providers |
| Swiss provider (e.g. Infomaniak) for everything | Swiss data residency as a sales argument | More ops work, no Vercel DX |

## Decision

`apps/web` on Vercel, root directory `apps/web`, region fra1, `turbo-ignore` to skip unaffected builds. `apps/core` on Fly.io in Frankfurt from `apps/core/Dockerfile`, one ~1 GB machine, scaled up only when needed. Swiss hosting is revisited for Enterprise.

## Consequences

JVM memory sized with `-XX:MaxRAMPercentage`. Costs tracked monthly against the CHF 400 loss cap.
