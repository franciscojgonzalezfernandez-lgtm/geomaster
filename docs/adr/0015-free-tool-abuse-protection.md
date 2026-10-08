# ADR-0015: Abuse protection for the free tool

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-FREE-05, FR-OPS-02

## Context

The anonymous live check spends real money per request.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Cloudflare Turnstile + rate limits in Postgres + budget guard | No extra infra; ties directly to spend | Postgres load for counters |
| Upstash rate limiting | Purpose-built | Another vendor |

## Decision

Turnstile on every anonymous check, rate limits per IP and per domain stored in Postgres, cached results per domain for 24 h, and the budget guard pauses the free tool first when the monthly cap is at risk.

## Consequences

Limits tuned from beta data.
