# accounts-projects — Design

Implements: REQ-1..REQ-18 · Constrained by: ADR-0001, ADR-0005, ADR-0017, ADR-0010, ADR-0016

## Overview

Thin vertical slice that establishes the web↔core contract and tenant isolation.
`apps/web` owns authentication (Better Auth), session storage and the UI. `apps/core` owns the
account/project domain and is the only writer of business data it governs. Web calls core over
the typed OpenAPI client with a JWKS-signed service token; core enforces `account_id` on every query.

```
Browser → apps/web (Better Auth: magic link / Google; organization + JWT plugins)
           │  session in Postgres (Better Auth Kysely adapter, read/write; never migrates)
           │  mints short-lived EdDSA JWT {sub=userId, acct=accountId, plan} (JWT plugin)
           ▼
        apps/core REST (OpenAPI/springdoc) → Postgres (Flyway-owned, account_id everywhere)
                     ▲ verifies token against web JWKS endpoint (no shared secret)
```

## Components and interfaces

### apps/web
- Better Auth with the magic-link plugin (email via Resend, ADR-0014) and the Google social
  provider; database sessions in Postgres via Better Auth's Kysely adapter over introspected types
  (ADR-0005: web never migrates). The **organization plugin** backs the tenant model (org ↔
  `account`, member ↔ `account_member`).
- Service-token minting: Better Auth's **JWT plugin** issues a short-lived (≤ 2 min) EdDSA JWT with
  `sub`, `acct`, `plan`, `iat`, `exp`; the signing key is exposed at the app's JWKS endpoint. One
  helper wraps the generated OpenAPI fetch client and attaches `Authorization: Bearer <token>` on
  every core call. No secret is shared with core.
- Minimal UI: sign-in page (public, must keep Lighthouse ≥ 0.9), "create project" form, project
  list. All strings from `packages/i18n` in EN/DE/FR/ES (NFR-08). Interface-language switcher
  writes `app_user.interface_locale` via core.

### apps/core
- `accounts` package: `AccountService`, `ProjectService`, repositories (JDBC), REST controllers.
- `ServiceTokenFilter`: validates the bearer token (EdDSA signature against the cached web JWKS,
  `exp`), puts `accountId`/`plan` into a request-scoped `TenantContext`. Rejects invalid tokens
  with 401 (REQ-5).
- Every repository method takes `accountId` from `TenantContext`; no query runs without it (REQ-6).
  Cross-account lookups return empty → controller maps to 404 (REQ-7).
- Plan limits read from `packages/plans/plans.json` at startup into a `Plans` component (Java reads
  the JSON directly, ADR-0010); never hard-coded (REQ-11).
- springdoc emits `packages/api-contract/openapi.yaml`; `openapi-typescript` regenerates web types in
  the same PR (ADR-0016); CI fails on drift.

### REST endpoints (core)
- `POST /v1/users/me` — upsert current user from token (first sign-in provisioning, REQ-2/REQ-3).
- `PATCH /v1/users/me` — set `interface_locale` (REQ-15).
- `GET /v1/account` — current account + plan.
- `POST /v1/projects` — create project (idempotent on domain, REQ-8..REQ-13).
- `GET /v1/projects` / `GET /v1/projects/{id}` — list / fetch (account-scoped, REQ-6/REQ-7).

## Data model

Flyway `V2__accounts_projects.sql` (ADR-0005). Business tables below; **Better Auth tables are also
created by Flyway** with table names pinned in Better Auth config to avoid colliding with the
business `account` table (Better Auth's own "account" means an OAuth provider link, not a tenant).
Web's Better Auth Kysely adapter maps to these names and reads/writes them but never migrates.

Business tables:
- `account` (tenant): `id uuid pk`, `name`, `plan_id text` (matches `plans.json` id), `created_at`.
- `app_user`: `id uuid pk`, `email citext unique`, `name`, `image`, `interface_locale`
  (`en|de|fr|es`, default `en`), `created_at`.
- `account_member`: `account_id fk`, `user_id fk`, `role` (`owner|member`), pk `(account_id,user_id)`.
- `project`: `id uuid pk`, `account_id fk`, `domain text`, `country char(2)`, `region text null`,
  `status`, `created_at`; unique `(account_id, domain)` (REQ-10).
- `project_language`: `project_id fk`, `locale` (`en|de|fr|es`), pk `(project_id, locale)` (REQ-8).

Better Auth tables (created by Flyway, owned-at-rest by web via its Kysely adapter): `auth_session`,
`auth_identity` (OAuth provider links = Better Auth "account"), `auth_verification`. `app_user` is
the Better Auth user table. The organization plugin reuses business `account` / `account_member`.

Every tenant-owned table carries `account_id` (ADR-0005). Indexes on `project(account_id)`.

## Error handling and idempotency
- Token invalid/expired/missing → 401, no DB access (REQ-5).
- Cross-account resource → 404, not 403 (REQ-7).
- Invalid domain → 422 (REQ-9); duplicate domain in same account → 200 with existing project (REQ-10).
- Plan-limit breach (domains/languages, later seats) → 422 naming the limit (REQ-12..REQ-14).
- Project create is idempotent on `(account_id, normalised domain)`; safe to retry.

## Cost and limits
- No LLM calls in this spec → no per-response cost (that starts in `scan-engine`).
- Only limits enforced here: `domains`, `languages` (from `plans.json`); `seats` is L.
- Service tokens are short-lived (≤ 2 min) to bound replay risk (NFR-05).

## Testing strategy
- Core (JUnit + Testcontainers `pgvector/pgvector:pg17`): first-sign-in provisioning (one account),
  returning user reuse, tenant isolation (account A cannot read account B → 404), domain
  normalisation + idempotent create, plan-limit rejections, invalid-token 401.
- Contract: CI regenerates `openapi.yaml` and fails on drift (ADR-0016).
- Web (Vitest): plan-limit helper reads `plans.json`, no hard-coded numbers; token minting shape.
- Web (Playwright): sign-in (magic-link dev transport) → create project → see it listed.
- Lighthouse CI on `/[locale]/signin` ≥ 0.9 in all four categories (NFR-09, ADR-0013).
- i18n: assert sign-in and create-project strings exist in EN/DE/FR/ES (NFR-08).
