# scan-engine — Design

Implements: REQ-1..REQ-18 · Constrained by: ADR-0004, ADR-0005, ADR-0007, ADR-0010, ADR-0012

## Overview

```
POST /v1/projects/{id}/scans ─┐          weekly scheduler ─┐
                              ▼                            ▼
                       ScanPlanner (plan-gated fan-out, budget guard, 1 active scan/project)
                              │ inserts scan + scan_unit rows (idempotency key per unit)
                              ▼
                 scan_unit queue (Postgres, SELECT … FOR UPDATE SKIP LOCKED)
                              │ virtual-thread workers, per-provider semaphores
                              ▼
            ResponseCache ──hit──► link existing llm_call
                 │ miss
                 ▼
      ProviderAdapter (openai | gemini | claude) — official Java SDKs (ADR-0007)
                 │ text, citations, searches, usage, model version, search suggestions
                 ▼
     CostCalculator (prices from config) → llm_call + answer + answer_citation
                              │
                 ScanFinalizer: completed | partial | failed, totals
```

## Components and interfaces

- `providers` package
  - `ProviderAdapter.answerWithSearch(AnswerRequest) → ProviderAnswer` where
    `AnswerRequest(promptText, locale, country, region, maxSearches)` and
    `ProviderAnswer(text, List<Citation>, List<String> searchQueries, int searchCount, Usage tokens, String model, String modelVersion, String searchSuggestionsHtml, Duration latency)`.
  - Implementations: `OpenAiAdapter` (Responses API, `web_search` tool, `user_location`), `GeminiAdapter`
    (`google_search` grounding; stores `searchEntryPoint.renderedContent`), `ClaudeAdapter` (web search
    server tool with `max_uses` and `user_location`). `FakeAdapter` for tests and local dev (no keys).
  - `ProviderErrors` classifies failures: retryable (timeout, 429, 5xx) vs permanent (4xx, refusal).
- `ProviderConfig` (`geomaster.providers.<engine>.*`): enabled, model, api-key (env only), max concurrency,
  max searches per call, request timeout, prices (input/output per 1M tokens, per 1k searches) + `price-version`.
  Changing a model is configuration, not code (NFR-12).
- `CostCalculator`: tokens × price + searches × price, converted to CHF with a configured USD→CHF rate.
- `ResponseCache`: key = sha256(prompt text) + engine + model + locale + country + region + UTC date.
  A hit links the new answer to the existing `llm_call` (no provider call, cost 0 for that unit).
- `ScanPlanner`: reads plan from `Plans` (engines, languages, prompts, scanFrequency); first scan → 2 samples.
  Engines order for a 1-engine plan: `openai` first (Free = ChatGPT, PRD).
- `ScanQueueWorker`: N virtual threads; claims units with `FOR UPDATE SKIP LOCKED`, sets a lease
  (`locked_until`); expired leases are reclaimable (worker crash). Backoff: 30 s × 2^attempt, max 3 attempts.
- `ScanFinalizer`: when no unit is pending/running → status + totals.
- `BudgetGuard` interface (`allowScan(account, plan) → Decision`); default implementation allows all.
- `ScanPlanner` refusals are typed (`ScanRefusedException.Reason`: `ALREADY_ACTIVE`, `FREQUENCY`, `BUDGET`,
  `PROJECT_INACTIVE`, `NO_PROMPTS`, `NO_ENGINES`); the scans API (T6) maps them to HTTP. Failed scans do not
  count against the plan's frequency. Engines = the plan's first N in priority order that are enabled in
  config; languages = the project's, capped by the plan in canonical order (en, de, fr, es).
- `WeeklyScanScheduler`: `@Scheduled` hourly; starts scans for projects whose last scan is ≥ 7 days old
  and whose plan is `weekly`; guarded by a Postgres advisory lock so only one instance schedules.
- REST: `POST/GET /v1/projects/{id}/prompts`, `PATCH/DELETE /v1/projects/{id}/prompts/{promptId}`,
  `POST/GET /v1/projects/{id}/scans`, `GET /v1/scans/{id}`. Contract regenerated (ADR-0016).

## Data model (Flyway V3)

- `prompt`: `id`, `account_id`, `project_id`, `locale`, `topic` (6 values), `text`, `version int`,
  `deleted_at`, `created_at`. Edit = new version row semantics via `prompt_version`
  (`prompt_id`, `version`, `text`), so answers keep the text they were asked with.
- `scan`: `id`, `account_id`, `project_id`, `status` (queued|running|completed|partial|failed),
  `trigger` (manual|schedule), `samples`, `units_total`, `units_done`, `units_failed`, `cost_chf numeric(10,4)`,
  `started_at`, `finished_at`, `versions jsonb`. Partial unique index: one queued/running scan per project.
- `scan_unit` (queue): `id`, `account_id`, `scan_id`, `prompt_id`, `prompt_version`, `engine`, `locale`,
  `sample`, `status` (pending|running|done|failed), `attempts`, `next_attempt_at`, `locked_until`,
  `last_error`, `llm_call_id`; **unique (scan_id, prompt_id, engine, locale, sample)** = idempotency key.
  Index `(status, next_attempt_at)`.
- `llm_call` (shared, not tenant-owned; the cache): `id`, `cache_key unique`, `engine`, `model`,
  `model_version`, `locale`, `country`, `region`, `prompt_sha256`, `answer_text`, `search_queries jsonb`,
  `search_count`, `input_tokens`, `output_tokens`, `cost_chf`, `price_version`, `search_suggestions_html`,
  `latency_ms`, `created_at`.
- `llm_call_citation`: `llm_call_id`, `position`, `url`, `title`, `domain`.
- Tenant view: `scan_unit.llm_call_id` links an account's unit to the (possibly shared) call; the
  cost an account is charged is the cost of calls its units caused (cache hit → 0).
- All tenant-owned tables carry `account_id` and `ON DELETE CASCADE` from `account` (REQ-18 of accounts).

## Error handling and idempotency

- A unit is claimed, executed, and its result written in one short transaction after the call returns;
  the provider call itself is outside the DB transaction. If the worker dies after the call but before
  commit, the unit's lease expires and it runs again — the cache key makes the second call a hit when the
  first one was stored, otherwise one extra call is the accepted worst case.
- Permanent errors fail the unit immediately; retryable ones back off. A provider with N consecutive
  failures is paused for 5 minutes (circuit breaker) so it does not burn the others' time.
- A scan never waits on another provider: units are independent and per-provider semaphores bound load.

## Cost and limits

- Budget: ≤ CHF 0.05 per answer (PRD). Searches per call capped (`max-searches`, default 2).
- Cache hits cost 0. First scan doubles units (2 samples) — accounted in PRD economics.
- `llm_call.cost_chf` is the real cost; the budget guard and backoffice (later spec) sum it.

## Testing strategy

- Unit: cost calculation from config prices; cache key; error classification; planner fan-out per plan
  (limits read from `plans.json`).
- Integration (Testcontainers + `FakeAdapter`): scan completes; retry never repeats a done unit; one
  provider failing → `partial`, others complete; cache hit across two accounts on the same day; lease
  expiry recovery; one active scan per project (409); Free plan uses only `openai`; budget guard refusal.
- Adapter contract tests against recorded provider responses (JSON fixtures), no network in CI.
- A manual, keyed smoke run (`./gradlew :core:providerSmoke`, needs API keys) asks one real prompt per
  engine and prints cost — run before enabling each adapter in production.
