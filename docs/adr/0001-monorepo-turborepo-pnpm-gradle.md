# ADR-0001: Polyglot monorepo with Turborepo, pnpm and Gradle

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-BIL-03, NFR-11

## Context

GEOMASTER has a TypeScript web app and a Java core service that share a database schema, an API contract and plan limits. One owner (10 h/week) works with agents that benefit from seeing the whole system in one repository.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Turborepo + pnpm + Gradle (root wrapper) | Owner already knows Turborepo; native Vercel support; each language keeps its own build tool | Turborepo does not see Gradle's internal graph (Gradle has its own cache) |
| Nx + Gradle plugin | Real dependency graph across TS and Java | More configuration, new learning curve |
| Two repositories | Simple per-repo CI | Contract and plan limits drift; agents lose context |

## Decision

One repository. pnpm 12 workspaces for `apps/web` and `packages/*`; Gradle 9 with a root wrapper and `apps/core` as project `:core`. Turborepo orchestrates both: `apps/core/package.json` scripts call `./gradlew :core:…`.

## Consequences

Single PR can change contract, Java and TypeScript together. Two toolchains to keep updated. CI is split per app with path filters.
