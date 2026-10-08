# GEOMASTER

GEO (Generative Engine Optimization) visibility platform for the Swiss/DACH market: measures how ChatGPT, Gemini and Claude mention a brand, in EN, DE, FR and ES, and turns the gaps into verified actions.

- Product site: https://geo-masterizer.com
- Status: pre-beta (closed beta from 2026-12-14)
- Repository: private. Public case study planned at launch.

## Stack

Monorepo (pnpm + Turborepo + Gradle): `apps/web` Next.js 16 on Vercel · `apps/core` Java 25 + Spring Boot 4 on Fly.io · PostgreSQL 17 + pgvector. See `docs/architecture.md` and `docs/adr/`.

## Getting started

Prerequisites: Node 22+, pnpm 12 (`corepack enable`), Docker (local Postgres and Testcontainers). JDK 25 is downloaded automatically by the Gradle toolchain if missing.

```bash
pnpm install
docker compose up -d db
cp .env.example .env
pnpm build      # builds web, shared packages and the Java core
pnpm dev        # runs web and core in development mode
```

## Where things live

| Path | Content |
| --- | --- |
| `docs/PRD.md` | Product Requirements Document (snapshot of the living doc) |
| `docs/architecture.md`, `docs/adr/` | Architecture overview and decisions |
| `specs/` | Spec Driven Development: requirements, design, tasks per feature |
| `CLAUDE.md` | Working agreement for Claude Code and other agents |

## Workflow

PRD → ADRs → specs (requirements → design → tasks) → implementation via PRs reviewed by agents and by the owner.
