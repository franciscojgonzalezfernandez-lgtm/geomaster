# Handoff from the claude.ai planning chat (2026-10-08)

Context that is not fully captured in the PRD or the ADRs. Read after `docs/PRD.md` and `docs/architecture.md`.

## Product decisions and their reasons

- Product name GEOMASTER, domain geo-masterizer.com (geopro.ch was taken).
- Functional reference: GenScore (genscore.es). GEOMASTER replicates its loop (measure → recommend → generate artifacts → verify) and improves it with: EN/DE/FR/ES and country/region per project, confidence intervals on every metric, verified impact of recommendations, controlled unit cost.
- GenScore observations used as benchmark: GEO Score with 5 axes (presence, prominence, share of voice, authority, technical); prompts grouped in 6 intent topics; recommendations with priority, impact/effort/confidence, literal evidence, affected prompts, "+X pt"; artifacts (comparison, content brief, lead paragraph, FAQ, schema) only in Pro; one free scan; free anonymous ChatGPT check; blog with 5 clusters. Its pricing page and free-tool page contradict each other on engines in the Free plan: GEOMASTER keeps one source of truth (`packages/plans/plans.json`).
- Artifacts were first removed and then restored by the owner: keep them, generated with RAG, and keep recommendation quality high.
- ICP: Swiss/DACH SMEs and large enterprises. Enterprise plan is custom, post-launch (SSO, roles, audit log, invoicing, DPA, SLA; indicative minimum CHF 1,500/month).
- Premium includes 90 min/month of consulting with the owner, valued at CHF 100/h.
- Dogfooding only with rideflumserberg.ch (owner's snowboard school). GenScore baseline: 20 % mention rate, scan of 2026-09-20.

## Economics (validate in beta)

- Cost assumption: CHF 0.05 per response (prompt × engine × language × run, with web search), CHF 0.25 per scan for recommendations, CHF 0.10 per artifact.
- Web search pricing seen on 2026-10-08: OpenAI $10 / 1k calls; Claude $10 / 1k searches; Gemini 3 grounding $14 / 1k queries after 5,000 free per month.
- Hard rule: monthly loss never above CHF 400. Fixed costs ~CHF 130 (Vercel already paid ~USD 21/month). Break-even ≈ 5 paying customers.
- Daily scanning is deliberately an add-on, not a plan feature (cost).

## Working preferences of the owner

- Spec Driven Development; uses Kiro and Claude Code; agent PR review workflows in GitHub Actions (copy from the owner's other repos).
- ~10 h/week. Closed beta 2026-12-14, public launch 2027-02-15.
- Wants Java in the portfolio: domain lives in `apps/core` (Spring Boot), UI in `apps/web` (Next.js).
- Product conversation in Spanish; repository artifacts in English.

## Open items

- [ ] Export the living PRD to `docs/PRD.md` (link inside that file).
- [ ] Brand and design phase (2026-10-19 → 2026-11-01): tokens feed `packages/ui`.
- [ ] Next step being decided: start specs with `scan-engine` + provider layer, or with `accounts-projects` for an end-to-end slice.
- [ ] Deploy spike: confirm Neon plan/cost (ADR-0005) and Fly.io machine size (ADR-0006).
- [ ] Queue spike: own SKIP LOCKED queue vs JobRunr fallback (ADR-0004).
- [ ] Legal: VAT/EU OSS with an advisor; trademark check for GEOMASTER (Swissreg, EUIPO); confirm geo-masterizer.com registration.
- [ ] Choose the 8 SMEs invited to the beta.
