# accounts-projects — Tasks

One task ≈ one PR, < 400 changed lines where possible; each PR references its REQ/FR/ADR IDs.
Ordered so the slice is demonstrable end-to-end as early as possible.

- [ ] T1 — Flyway `V2__accounts_projects.sql`: `account`, `app_user`, `account_member`, `project`,
      `project_language` + `auth_*` tables; `citext`, indexes, `(account_id, domain)` unique.
      (REQ-2, REQ-8, REQ-10, REQ-17; ADR-0005)
- [ ] T2 — Core domain + repositories with `TenantContext`; every query scoped by `account_id`.
      (REQ-6, REQ-7; ADR-0003, ADR-0005)
- [ ] T3 — `ServiceTokenFilter`: verify EdDSA JWT against cached web JWKS, populate `TenantContext`,
      401 on failure. (REQ-4, REQ-5; ADR-0017)
- [ ] T4 — `Plans` component in core reading `packages/plans/plans.json`; no hard-coded limits.
      (REQ-11; ADR-0010)
- [ ] T5 — Core REST: `POST/PATCH /v1/users/me`, `GET /v1/account`, `POST/GET /v1/projects`
      with domain normalisation, idempotent create, plan-limit checks. (REQ-2, REQ-3, REQ-8..REQ-13, REQ-15)
- [ ] T6 — springdoc OpenAPI → `packages/api-contract/openapi.yaml`; `openapi-typescript` types;
      CI drift check. (ADR-0016)
- [ ] T7 — Core tests (JUnit + Testcontainers): provisioning, returning-user reuse, tenant
      isolation 404, idempotent create, plan-limit 422, invalid-token 401. (REQ-2..REQ-13)
- [ ] T8 — Web: Better Auth (magic-link plugin via Resend + Google social + organization plugin),
      Postgres session via the Kysely adapter over `app_user`/`auth_*`; first-sign-in calls
      `POST /v1/users/me`. (REQ-1, REQ-2, REQ-3; ADR-0017, ADR-0014)
- [ ] T9 — Web: enable Better Auth JWT plugin (EdDSA + JWKS endpoint); service-token helper wrapping
      the typed core client. (REQ-4; ADR-0017)
- [ ] T10 — Web UI: sign-in page, create-project form, project list; interface-language switcher
      persisting `interface_locale`; all strings in `packages/i18n` EN/DE/FR/ES. (REQ-15, REQ-16; NFR-08)
- [ ] T11 — Web tests: Vitest (plan-limit helper, token shape), Playwright (sign-in → create →
      list), Lighthouse CI ≥ 0.9 on `/[locale]/signin`. (NFR-09; ADR-0013)
- [ ] T12 — Account deletion endpoint + UI removing all data for the `account_id`. (REQ-18; NFR-06)
- [x] T13 — Core smoke job: boot core against ephemeral pgvector Postgres, assert `/actuator/health`
      UP (actuator already in `build.gradle.kts`). Done in `.github/workflows/ci-smoke.yml`. (ADR-0013)
- [ ] T14 — Web smoke currently asserts `/` 200 (`ci-smoke.yml`); switch target to `/[locale]/signin`
      once the sign-in route lands. (ADR-0013 smoke gate)
- [ ] T15 — Contract-drift CI: gradle task exports springdoc OpenAPI, CI regenerates and
      `git diff --exit-code` against `packages/api-contract/openapi.yaml`. (ADR-0016)
