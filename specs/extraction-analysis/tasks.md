# extraction-analysis — Tasks

🔑 = needs `ANTHROPIC_API_KEY` (Fly secret for production, repo secret for the CI eval).

- [x] T1 — Flyway `V6__extraction_analysis.sql`: `brand`, `brand_alias`, `answer_extraction`, `answer_brand`,
      `scan_mention`, `scan_citation`, new `scan` columns; backfill an own brand for existing projects. (REQ-1, REQ-16..REQ-18)
- [x] T2 — Brands API: own brand created with the project, rename, aliases, competitors within
      `competitorsPerDomain`; soft deletes; contract regenerated. Includes `BrandNormalizer` (keys are needed
      for the duplicate check). (REQ-1..REQ-3, REQ-12, REQ-23)
- [ ] T3 — `GroundingCheck`, `PositionLocator`, `UrlScanner` (pure Java, table tests). (REQ-7..REQ-9, REQ-12)
- [ ] T4 — 🔑 `extractStructured` on `ProviderAdapter`, shared `AnthropicClients`, Claude Haiku 5.5 structured
      output with prompt v1, schema v1, `ExtractorVersion` fingerprint pin, extractor prices in config. (REQ-5, REQ-6, REQ-11, REQ-17)
- [ ] T5 — `ExtractionEnqueuer` in `UnitExecutor` + `ExtractionWorker` (claim, lease, retry, refusal/invalid
      output terminal), cost recorded, extraction span. (REQ-4, REQ-10, REQ-16, REQ-19)
- [ ] T6 — `MentionResolver` + `ScanAnalyser`: per-scan resolution, citation classes, scan analysed with
      totals and versions; `ReResolveJob` after brand edits. (REQ-12..REQ-15, REQ-18)
- [ ] T7 — Mentions API `GET /v1/scans/{id}/mentions`; contract regenerated. (REQ-24)
- [ ] T8 — Eval dataset v1: ≥ 30 labelled answers per language from real scans (owner reviews labels). (REQ-20)
- [ ] T9 — 🔑 `:core:extractorEval` scoring and report + `extractor-eval` CI job (path-gated, weekly) as a
      required check. (REQ-21, REQ-22)
- [ ] T10 — Web project page: brands editor (own brand name and aliases, competitors with plan limit,
      localized EN/DE/FR/ES) and per-answer mentions under the scan. (REQ-1..REQ-3, REQ-23, REQ-24)
