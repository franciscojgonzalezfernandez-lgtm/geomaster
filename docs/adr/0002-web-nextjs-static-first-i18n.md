# ADR-0002: Next.js App Router for the web, static-first rendering and next-intl

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-DSH, FR-FREE, FR-BLOG, NFR-08, NFR-09

## Context

Public pages (12 free-tool landings × locales, blog, pricing) must score ≥ 90 in all four Lighthouse categories on mobile and desktop, in EN, DE, FR and ES. The app (dashboard) needs authentication and dynamic data.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Next.js 16 App Router | One framework for public pages and app; RSC keeps client JS low; native on Vercel | Discipline needed to keep client bundles small |
| Astro (public) + Next.js (app) | Best-in-class Lighthouse for content pages | Two web frameworks, duplicated layout and i18n |

## Decision

Next.js 16 App Router with TypeScript strict and Tailwind CSS v4. Public routes are static or ISR; the dashboard is dynamic behind auth. i18n with next-intl and localized URL prefixes (`/en`, `/de`, `/fr`, `/es`) plus `hreflang`. Performance rules: Server Components by default, client components only for interaction, `next/font` self-hosted, `next/image`, no third-party scripts on public pages.

## Consequences

Lighthouse budgets enforced in CI (ADR-0013). Fonts and tokens come from the brand design phase.
