# ADR-0003: Java 25 + Spring Boot 4 core service for the domain

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-ONB, FR-SCN, FR-EXT, FR-REC, FR-ART, FR-AUD, FR-OPS

## Context

The domain logic (scans, extraction, GEO Score, agents, RAG, artifacts, budget guard) is concurrency-heavy and long-running, and the owner wants a production Java service in the portfolio.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Spring Boot core (domain) + Next.js (UI, auth, billing) | Clear boundary; Java does the hard engineering; web stays light | Two runtimes in production |
| Everything in Next.js/Node | One runtime | No Java in the portfolio; long-running jobs awkward on serverless |
| Full backend in Java | Maximum Java | Duplicates session, webhooks and billing logic; risk for the beta date |

## Decision

`apps/core`: Java 25, Spring Boot 4.1, virtual threads enabled. A modular monolith with packages `scan`, `providers`, `extraction`, `scoring`, `agents`, `rag`, `artifacts`, `audit`, `budget`. It exposes an internal REST API (ADR-0016) and never serves HTML. `apps/web` never calls an LLM directly.

## Consequences

Two deployables. The boundary rule is checked in review: UI concerns in web, domain in core.
