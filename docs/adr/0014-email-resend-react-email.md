# ADR-0014: Email with Resend and React Email templates

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-NOT-01..02, FR-ACC-01

## Context

Magic links, scan reports and weekly digests in four languages.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Resend + React Email | Templates in the same TS codebase | Another vendor |
| Postmark | Excellent deliverability | Templates outside the codebase |

## Decision

Resend for sending; templates with React Email in `apps/web`, localized with the same messages as the UI. Core triggers notifications through the web API or an outbox table.

## Consequences

Domain authentication (SPF, DKIM, DMARC) for geo-masterizer.com before beta.
