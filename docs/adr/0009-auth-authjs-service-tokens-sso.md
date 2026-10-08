# ADR-0009: Authentication: Auth.js in web, service tokens to core, SSO later

- Status: Superseded by [ADR-0017](0017-auth-better-auth-jwks.md) (2026-10-09)
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-ACC-01, FR-ACC-03, Enterprise

> **Superseded.** The library choice (Auth.js) is replaced by Better Auth in ADR-0017.
> The service-token-to-core and tenant-isolation decisions below still hold; ADR-0017
> only changes the web auth library and upgrades the token to a JWKS-verified JWT.

## Context

Users sign in with email magic link or Google. Enterprise customers will need SAML/OIDC SSO after launch.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Auth.js in web + signed service tokens to core | Owner knows NextAuth; core stays stateless | SSO needs an extra provider later |
| Auth in core (Spring Security) | One auth system | Duplicated session handling with web |

## Decision

Auth.js (email magic link + Google) in `apps/web`, sessions in Postgres. Calls from web to core carry a short-lived signed token with user, account and plan. Enterprise SSO via WorkOS (or equivalent) when the Enterprise plan ships.

## Consequences

Core validates tokens and enforces tenant isolation on every query.
