# ADR-0012: Observability: OpenTelemetry, Langfuse and Sentry

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: NFR-04, NFR-10, NFR-13, FR-EXT-03

## Context

We must trace each scan and each LLM call, see cost per tenant and run evals.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| OpenTelemetry + Langfuse + Sentry | LLM-native traces, cost and eval datasets; standard telemetry | Three tools |
| Single APM vendor | One tool | Weak LLM-specific features, higher cost |

## Decision

OpenTelemetry Java agent in core and Next.js instrumentation in web; LLM spans and costs to Langfuse (EU cloud region); errors to Sentry. Cost per response is also stored in Postgres for the budget guard.

## Consequences

Free tiers first; review cost at 15 paying customers.
