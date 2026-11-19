# scan-engine — Tasks

One task ≈ one PR. Tasks marked 🔑 need a provider API key to verify against the real API (CI uses
recorded fixtures).

- [x] T1 — Flyway `V3__scan_engine.sql`: `prompt`, `prompt_version`, `scan`, `scan_unit`, `llm_call`,
      `llm_call_citation`; idempotency unique key, one-active-scan partial index. (REQ-1, REQ-2, REQ-12)
- [x] T2 — Prompts API: add/edit/delete/list within the plan's `prompts` limit; versions kept. (REQ-1, REQ-2)
- [x] T3 — `ProviderAdapter` interface, `ProviderConfig` (model, prices, limits from config), `CostCalculator`,
      `FakeAdapter`. (REQ-7, REQ-11, REQ-14; ADR-0007)
- [x] T4 — Queue + workers: SKIP LOCKED claim, leases, backoff, per-provider semaphores, circuit breaker.
      (REQ-9, REQ-10, REQ-11; ADR-0004)
- [ ] T5 — `ScanPlanner` + `ScanFinalizer` + `ResponseCache` + `BudgetGuard` interface. (REQ-3..REQ-6, REQ-8, REQ-15, REQ-18)
- [ ] T6 — Scans API: start, list, detail with progress; contract regenerated. (REQ-16, REQ-17)
- [x] T7 — 🔑 `OpenAiAdapter` (Responses API + `web_search`, user location, max searches). (REQ-7, REQ-12)
- [ ] T8 — 🔑 `GeminiAdapter` (Google Search grounding, search entry point stored). (REQ-7, REQ-12, REQ-13)
- [ ] T9 — 🔑 `ClaudeAdapter` (web search tool, `max_uses`, user location). (REQ-7, REQ-12)
- [ ] T10 — Weekly scheduler with advisory lock. (REQ-5)
- [ ] T11 — OpenTelemetry spans per scan and per call; Langfuse export config. (NFR-10; ADR-0012)
- [ ] T12 — `providerSmoke` gradle task: one real prompt per engine, prints answer/citations/cost. 🔑
