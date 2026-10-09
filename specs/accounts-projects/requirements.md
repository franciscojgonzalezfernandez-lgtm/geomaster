# accounts-projects — Requirements

Traces to: FR-ACC-01, FR-ACC-02, FR-ACC-03, FR-ACC-04, NFR-05, NFR-06, NFR-08
Constrained by: ADR-0017 (Better Auth), ADR-0005, ADR-0010, ADR-0016
Phase: B (multi-seat enforcement from FR-ACC-03 is L)

This is the first end-to-end slice. It proves the plumbing every later spec depends on:
Better Auth sign-in (web) → JWKS-signed service token → core REST (OpenAPI contract) → Flyway-owned
Postgres with `account_id` tenant isolation.

## User stories

- As a visitor, I want to sign in with an email magic link or with Google so that I can access the app without a password.
- As a signed-in user, I want an account created for me on first sign-in so that my data is isolated from other customers.
- As a user, I want to create a project (one domain, one or more measurement languages, a country and an optional region) so that later scans know what to measure and where.
- As a user, I want to pick the interface language independently of the languages I measure so that I can read the app in my language while measuring others.
- As a user on a given plan, I want the app to stop me from exceeding my plan's domain and measurement-language limits so that billing and cost stay predictable.

## Acceptance criteria (EARS)

### Authentication and account provisioning (FR-ACC-01)
- REQ-1: WHEN a visitor requests a magic link for a valid email address, THE SYSTEM SHALL email a single-use, time-limited sign-in link and create no session until the link is used.
- REQ-2: WHEN a user completes sign-in by magic link or Google for the first time, THE SYSTEM SHALL create one `app_user` and one personal `account` (tenant) on the `free` plan, linked by an owner membership.
- REQ-3: WHEN a returning user signs in, THE SYSTEM SHALL reuse the existing `app_user` and SHALL NOT create a second account.
- REQ-4: WHILE a user is signed in, THE SYSTEM SHALL issue every web→core call a short-lived JWKS-signed service token carrying user id, account id and plan id (ADR-0017).
- REQ-5: IF a service token is missing, expired or fails JWKS signature verification, THEN core SHALL reject the call with 401 and perform no read or write.

### Tenant isolation (NFR-05)
- REQ-6: WHILE serving any request, core SHALL scope every tenant-owned query to the `account_id` carried by the service token.
- REQ-7: IF a request references a resource owned by a different account, THEN core SHALL respond 404 (not 403) so resource existence does not leak across tenants.

### Projects (FR-ACC-02)
- REQ-8: WHEN a user creates a project, THE SYSTEM SHALL require exactly one domain, at least one measurement language from {en, de, fr, es}, and a country (ISO 3166-1 alpha-2); a region is optional.
- REQ-9: WHEN a user submits a domain, THE SYSTEM SHALL normalise it (lowercase, strip scheme/`www.`/trailing slash) and reject a syntactically invalid domain with 422.
- REQ-10: IF a user creates a project whose domain already exists in the same account, THEN THE SYSTEM SHALL treat the create as idempotent and return the existing project rather than a duplicate.
- REQ-11: THE SYSTEM SHALL read all plan limits from `packages/plans/plans.json` and SHALL NOT hard-code any limit.

### Plan limits (FR-ACC-02, FR-ACC-03)
- REQ-12: IF creating a project would exceed the account plan's `domains` limit, THEN THE SYSTEM SHALL reject the create with 422 and a message naming the limit.
- REQ-13: IF a project's selected measurement languages exceed the plan's `languages` limit, THEN THE SYSTEM SHALL reject the create with 422.
- REQ-14 (L): IF inviting a member would exceed the plan's `seats` limit, THEN THE SYSTEM SHALL reject the invite with 422.

### Interface language (FR-ACC-04)
- REQ-15: WHEN a user sets the interface language to one of EN/DE/FR/ES, THE SYSTEM SHALL persist it on `app_user` independently of any project's measurement languages.
- REQ-16: WHILE rendering the app, THE SYSTEM SHALL use the user's stored interface language, falling back to the `Accept-Language` header and then EN.

### Data residency and account lifecycle (NFR-06)
- REQ-17: THE SYSTEM SHALL store all account and project data in an EU/CH region (ADR-0005, ADR-0006).
- REQ-18: WHEN a user requests account deletion, THE SYSTEM SHALL remove the account and all data owned by its `account_id` (self-service export/delete; export detail deferred to a later spec).

## Out of scope

- Onboarding crawl, competitor and prompt generation (`onboarding-agent`).
- Billing, Stripe, plan changes and paid seats purchase (`billing`); this spec only reads plan limits.
- Enterprise SSO (SAML/OIDC), roles beyond owner/member, audit log (`enterprise`).
- Scan execution, GEO Score, dashboard data (`scan-engine`, `geo-score`, `dashboard`).
