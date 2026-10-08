# CLAUDE.md — GEOMASTER

You are working on GEOMASTER (geo-masterizer.com), a multilingual GEO visibility SaaS that measures how ChatGPT, Gemini and Claude mention a brand, for Swiss/DACH SMEs and enterprises.

## Sources of truth (read before coding)

Context imports: @docs/PRD.md @docs/architecture.md @docs/HANDOFF.md

1. `docs/PRD.md` — requirements. IDs: `FR-<MODULE>-NN`, `NFR-NN`. Phase tags: (B) beta, (L) launch.
2. `docs/architecture.md` and `docs/adr/` — accepted decisions. Never contradict an accepted ADR; propose a superseding ADR instead.
3. `specs/<feature>/` — `requirements.md` (EARS), `design.md`, `tasks.md`. Implement only tasks that exist in a spec.

## Stack

- Monorepo: pnpm 12 workspaces + Turborepo 2; Gradle 9 root wrapper (`./gradlew :core:…`).
- `apps/web`: Next.js 16 App Router, TypeScript strict, Tailwind v4, next-intl, Better Auth (ADR-0017), Stripe. Read `apps/web/AGENTS.md` before writing Next.js code.
- `apps/core`: Java 25, Spring Boot 4.1, virtual threads, JDBC + Flyway, PostgreSQL 17 + pgvector, springdoc OpenAPI.
- Shared: `packages/plans` (limits), `packages/api-contract` (OpenAPI + TS types), `packages/i18n`, `packages/ui`.

## Boundaries (non-negotiable)

- Only `apps/core` calls LLM providers, through the `ProviderAdapter` layer; every call records provider, model, version, tokens, searches and cost.
- Only Flyway migrations in `apps/core/src/main/resources/db/migration` change the schema. Web never migrates.
- `apps/core` never renders HTML. `apps/web` never calls an LLM.
- Plan limits are read from `packages/plans/plans.json`. Never hard-code a limit.
- When a core endpoint changes, regenerate `packages/api-contract` in the same PR.

## Quality rules

- TypeScript strict; no `any` without a justifying comment. Java: records for DTOs, no field injection, constructor injection only.
- No secrets in code, logs or prompts. Keys only server-side, from environment variables.
- All user-facing strings in EN, DE, FR, ES. No string literals in UI components.
- Public pages keep Lighthouse ≥ 90 in Performance, Accessibility, Best Practices and SEO (mobile and desktop). WCAG 2.1 AA.
- Scan units are idempotent and retry-safe. Every tenant-owned query filters by `account_id`.
- Gemini grounded answers shown in the UI display Google Search Suggestions.
- Extractor changes must pass the eval suite thresholds.

## Workflow

- Spec Driven Development: PRD → ADR → `specs/<feature>/` (from `specs/_template/`) → tasks → PRs.
- One spec task per PR, < 400 changed lines where possible. PRs reference spec, task and requirement IDs.
- Code, commits, specs and ADRs in English. Conventional Commits.

## Commands

- `pnpm install` · `pnpm dev` · `pnpm build` · `pnpm lint` · `pnpm typecheck` · `pnpm test`
- Java only: `./gradlew :core:build` · `./gradlew :core:bootRun`
- Local database: `docker compose up -d db`
