# ADR-0011: Blog as MDX in the repository, per locale

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-BLOG-01..03, NFR-09

## Context

Four languages, human review, excellent Lighthouse and GEO of our own pages.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| MDX in repo | Reviewed by PR, static, free | Non-developers cannot edit |
| Headless CMS | Editorial UI | Cost, extra runtime calls |

## Decision

`apps/web/content/blog/<locale>/<slug>.mdx`, statically generated, with structured data, `hreflang` and RSS per locale. AI-assisted drafts go through PR review.

## Consequences

Revisit a CMS if non-technical writers join.
