# scan-engine — Requirements

Traces to: FR-SCN-01..07, FR-OPS-03, NFR-02, NFR-04, NFR-10, NFR-12, NFR-13
Constrained by: ADR-0004 (own Postgres queue), ADR-0005, ADR-0007 (ProviderAdapter), ADR-0010 (plan limits), ADR-0012 (observability)
Phase: B

The scan engine asks ChatGPT, Gemini and Claude (with web search on) every prompt of a project,
in every measured language, and stores the literal answers with citations, model versions,
searches, tokens and real cost. Everything downstream (extraction, GEO Score, dashboard,
recommendations) reads what this spec stores. It must be reliable (≥ 99 % complete scans) and
cheap (≤ CHF 0.05 per answer, shared cache).

## User stories

- As a user, I want my project's prompts to be asked to each AI engine every week so that I can see how they mention my brand over time.
- As a user on the Free plan, I want one complete scan so that I can see where I stand before paying.
- As a user, I want to add, edit and delete the prompts that are measured, within my plan's limit, so that the scan reflects what my customers really ask.
- As the owner, I want every LLM call's real cost recorded and hard limits on searches so that a scan can never blow the monthly budget.
- As the owner, I want a failing provider to never block the other two so that scans still complete.

## Acceptance criteria (EARS)

### Prompts (minimal, before the onboarding agent exists)
- REQ-1: WHEN a user adds a prompt to a project, THE SYSTEM SHALL store its text, measurement language and topic (one of the 6 FR-ONB-03 topics), and reject it with 422 if the project's prompt count would exceed the plan's `prompts` limit (counted as prompt × language).
- REQ-2: WHEN a user edits or deletes a prompt, THE SYSTEM SHALL keep past answers linked to the prompt version they were asked with (NFR-13); a deleted prompt is not asked again.

### Scan planning
- REQ-3: WHEN a scan starts, THE SYSTEM SHALL create one unit per prompt × engine × language × sample, using only the engines and languages the account's plan allows (Free: ChatGPT only), from `plans.json`.
- REQ-4: WHEN a project's first scan starts, THE SYSTEM SHALL plan 2 samples per prompt (baseline); later scans plan 1 (FR-SCN-03).
- REQ-5: WHILE a plan's scan frequency is `weekly`, THE SYSTEM SHALL start one scan per project per week; WHILE it is `once`, THE SYSTEM SHALL allow exactly one scan per project.
- REQ-6: IF a scan for the same project is already queued or running, THEN THE SYSTEM SHALL not start a second one (409 for a manual start).

### Execution
- REQ-7: WHEN a unit runs, THE SYSTEM SHALL ask the engine's configured consumer-app default model with web search enabled, in the unit's language and with the project's country/region as user location where the provider supports it (FR-SCN-01).
- REQ-8: WHEN the same prompt text, engine, model, language, location and calendar day (UTC) was already answered for any account, THE SYSTEM SHALL reuse that answer instead of calling the provider (FR-SCN-04).
- REQ-9: WHEN a unit fails with a retryable error (timeout, 429, 5xx), THE SYSTEM SHALL retry it with exponential backoff up to the configured attempts; a retried scan SHALL never repeat a unit that already succeeded (FR-SCN-05).
- REQ-10: IF one provider keeps failing, THEN THE SYSTEM SHALL finish the other providers' units and mark the scan `partial` with the failed units listed (NFR-02).
- REQ-11: THE SYSTEM SHALL cap web searches per call at the configured limit (FR-OPS-03) and SHALL respect a per-provider concurrency/rate limit.

### Recording
- REQ-12: FOR every provider call THE SYSTEM SHALL store provider, model, model version, prompt version, language, location, literal answer text, cited URLs with titles, search queries performed, input/output tokens, search count, latency and real cost in CHF (FR-SCN-06, NFR-04).
- REQ-13: WHEN Gemini returns grounding metadata, THE SYSTEM SHALL store its Google Search Suggestions (search entry point) with the answer so the UI can show them (FR-SCN-07).
- REQ-14: THE SYSTEM SHALL compute cost from token and search prices held in configuration per model, never hard-coded, and record the price version used.
- REQ-15: WHEN a scan ends, THE SYSTEM SHALL set its status (`completed`, `partial`, `failed`), totals (units, answers, cost) and the versions used (formula/extractor slots stay empty until those specs).

### API
- REQ-16: WHEN a user starts a scan manually (`POST /v1/projects/{id}/scans`), THE SYSTEM SHALL enforce REQ-5/REQ-6 and return the scan with status `queued`.
- REQ-17: WHEN a user reads a scan, THE SYSTEM SHALL return its status, progress (units done / total / failed) and cost, account-scoped (404 for other accounts).

### Budget
- REQ-18: BEFORE starting a scan, THE SYSTEM SHALL ask the budget guard whether it may run; IF it refuses (Free scans paused, FR-OPS-02), THEN THE SYSTEM SHALL not start it and SHALL say why. (The guard's policy itself is the `budget-guard-backoffice` spec; here it is an interface that allows everything.)

## Out of scope
- Mention extraction, sentiment, entity resolution (`extraction-analysis`).
- GEO Score and confidence intervals (`geo-score`).
- Prompt generation by the onboarding agent (`onboarding-agent`); here prompts are entered by hand.
- Premium's 2× weekly runs on 25 key prompts (follow-up task once Premium exists).
- Showing answers in the UI (`dashboard`), emails (`notifications`).
- "Model memory" mode without search (backlog).
