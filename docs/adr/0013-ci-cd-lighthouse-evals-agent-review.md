# ADR-0013: CI/CD: per-app pipelines, Lighthouse budgets, evals and agent PR review

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: NFR-09, NFR-11, FR-EXT-03

## Context

Quality gates must run automatically so a solo owner can merge safely.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| GitHub Actions per app with path filters | Fast, cheap, clear ownership | Some duplication |

## Decision

Web: lint, typecheck, Vitest, Playwright and Lighthouse CI with assertions ≥ 0.9 for Performance, Accessibility, Best Practices and SEO on public routes. Core: Gradle build, JUnit + Testcontainers, extractor eval suite with thresholds. Contract drift check (ADR-0016). Agent PR review workflows (summary, vulnerabilities, quality score) reused from the owner's existing setup. Vercel deploys web; a workflow deploys core to Fly.io on main.

## Consequences

Merges blocked on red checks.
