# ADR-0010: Billing with Stripe; plan limits in packages/plans

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-BIL-01..04, FR-OPS-03

## Context

Self-serve plans in CHF and EUR, monthly and annual, early-bird coupons; VAT obligations to be validated with an advisor.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Stripe Billing + Checkout + Customer Portal + Stripe Tax | Standard, low effort, good docs | We stay the seller of record |
| Paddle (merchant of record) | Handles EU VAT as seller | Less flexible, higher fees |

## Decision

Stripe Billing with Checkout, Customer Portal and Stripe Tax; webhooks handled in `apps/web` and mirrored to the database. Plan limits live only in `packages/plans/plans.json`, read by web (TypeScript) and core (Java). Revisit Paddle if the advisor confirms heavy EU VAT obligations.

## Consequences

Changing a limit is a one-file PR that both apps pick up.
