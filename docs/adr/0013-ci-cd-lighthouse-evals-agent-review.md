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

Web: lint, typecheck, Vitest, Playwright and Lighthouse CI with assertions ≥ 0.9 for Performance, Accessibility, Best Practices and SEO on public routes. Core: Gradle build, JUnit + Testcontainers, extractor eval suite with thresholds. Contract drift check (ADR-0016).

**Smoke tests are a required gate.** On top of unit/integration tests and the Lighthouse ≥ 0.9 assertions, a `smoke` job must pass before a PR can merge: it boots the real artifacts and asserts the system actually comes up — web is built and started and its public routes return 200; core is started against an ephemeral Postgres and `/actuator/health` (and liveness of the job queue) is UP. A green build with a dead runtime does not pass.

**Automated PR review runs on GitHub, after CI is green**, with Claude cloud agents in fresh (clean) context that read the diff for correctness bugs and vulnerabilities and emit a quality score; conditional auto-approval is specified in ADR-0018. Vercel deploys web; a workflow deploys core to Fly.io on main.

## Consequences

Merges blocked on red checks: lint, typecheck, unit/integration tests, extractor evals, contract-drift, Lighthouse ≥ 0.9 and the smoke job must all be green, enforced by branch protection on `main`. The Claude PR-review verdict is an additional required check (ADR-0018).
