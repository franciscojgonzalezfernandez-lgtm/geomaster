# extraction-analysis — Design

Implements: REQ-1..REQ-24  ·  Constrained by: ADR-0005, ADR-0007, ADR-0010, ADR-0012, ADR-0013, ADR-0019

## Overview

Two stages, split so the expensive one is shared and the cheap one is per tenant:

1. **Extraction (shared, LLM).** One extraction per `llm_call` × extractor version. Brand-agnostic:
   the model lists every brand the answer names, with sentiment, recommended flag and an evidence
   quote. Cache hits across accounts reuse it, exactly like the answers themselves (FR-SCN-04).
2. **Resolution (per project, deterministic).** Maps extracted names to the project's own brand,
   competitors or `untracked`, classifies cited domains, and writes `scan_mention` rows. No model
   call, so it is free to re-run when the user edits brands (REQ-15).

```
scan_unit done ──► ensure answer_extraction(llm_call, ext-vN) ──► ExtractionWorker
                                                                   │ ProviderAdapter.extractStructured
                                                                   │ (Claude Haiku 5.5, JSON schema)
                                                                   ▼
                     grounding check + positions (Java) ──► answer_brand rows
scan finalized + all extractions terminal ──► MentionResolver(project brands) ──► scan_mention, scan_citation_class
                                                                   └──► scan.analysed_at, versions.extractor/resolver
```

## Components and interfaces

- **`ProviderAdapter.extractStructured(StructuredRequest) → StructuredResult`** (ADR-0007). Default
  method throws `UnsupportedOperationException`; implemented by the Claude adapter. `StructuredRequest`
  holds system prompt, user content blocks, JSON schema, `maxTokens`, effort. `StructuredResult` holds
  the raw JSON string, stop reason, model, model version, tokens, latency. Cost is computed by the
  existing `CostCalculator` from `geomaster.extractor.prices.*`.
- **`AnthropicClients`** (new, `providers.claude`): builds one thread-safe `AnthropicClient` from
  `ANTHROPIC_API_KEY` with the official Java SDK (`com.anthropic:anthropic-java`). Shared by the
  extractor and the later `ClaudeAdapter` (scan-engine T9), so T9 only adds `answerWithSearch`.
- **Extractor request** (Messages API):
  - model `claude-haiku-5-5` (config `geomaster.extractor.model`; NFR-12).
  - Structured outputs: `output_config.format` with type `json_schema` and the schema below.
  - Effort `low` (`output_config.effort`). Haiku 5.5 runs adaptive thinking by default; thinking
    `disabled` is accepted at effort `high` or below and is tried first, with the eval deciding (T4).
  - `max_tokens` 2,000.
  - No `temperature` (rejected on current API versions).
  - System prompt (fixed, cacheable): the extraction rules, with the answer passed in the user turn
    inside a delimited document block, labelled as data (REQ-11).
- **Output schema** (`extractor-v1.schema.json`, `additionalProperties: false` everywhere):

  ```json
  { "brands": [ { "name": "string", "domain": "string|null",
                  "sentiment": "positive|neutral|negative",
                  "recommended": "boolean", "evidence": "string ≤ 200 chars" } ] }
  ```

  "Brand" means a named company, product, venue, school or service that a customer could choose.
  Places, generic categories and the AI engines themselves do not count. Few-shot examples in all
  four languages live in the prompt.
- **`ExtractorVersion`**: `ext-<n>` from config, plus a fingerprint (sha256 of prompt template,
  schema, model, effort, thinking, max tokens). A unit test pins fingerprint → version, so changing
  any input without bumping the version fails the build (REQ-17).
- **`GroundingCheck` + `PositionLocator`** (pure Java): normalise (NFC, case-fold, collapse
  whitespace) and drop brands whose name and evidence are both absent from the answer (REQ-7). The
  position comes from the first index of name / alias / evidence in the text (REQ-8).
- **`UrlScanner`** (pure Java): literal URLs in the answer text are merged with `llm_call_citation`
  (REQ-9).
- **`ExtractionWorker`**: the same SKIP LOCKED claim, lease, backoff and circuit-breaker helpers as
  `ScanWorker`, on table `answer_extraction`. It has its own concurrency setting
  `geomaster.extractor.concurrency` (REQ-10).
- **`ExtractionEnqueuer`**: called by `UnitExecutor` in the same transaction that marks a unit
  `done`, via `INSERT … ON CONFLICT (llm_call_id, extractor_version) DO NOTHING` (REQ-4).
- **`BrandNormalizer` + `MentionResolver`**:
  - Normalisation: strip accents; lower-case; drop punctuation; drop legal suffixes; for domains,
    drop the scheme, `www.` and the TLD so the stem remains.
  - Matching is exact on normalised alias sets. Same-brand duplicates collapse to the earliest
    position (REQ-12..REQ-14).
  - The resolver version is `res-<n>`.
- **`ScanAnalyser`**:
  - Triggered after `ScanFinalizer` and whenever an extraction of that scan reaches a terminal
    state.
  - Once every `done` unit's extraction is terminal, it resolves the scan's mentions, adds the
    extraction cost and sets `analysed_at` and `versions` (REQ-16, REQ-18).
  - A `ReResolveJob` reruns the resolver for a project after brand edits (REQ-15).
- **`BrandsController`** `/v1/projects/{id}/brands` (GET, POST competitor, PATCH brand, DELETE
  brand / alias) and **`MentionsController`** `GET /v1/scans/{id}/mentions` (REQ-23, REQ-24). The
  OpenAPI contract is regenerated in the same PR.
- **Telemetry**: `ScanTelemetry.extraction(...)` emits the span `chat claude-haiku-5-5` with
  `gen_ai.operation.name=chat`, usage, `geomaster.cost_chf`, `geomaster.extractor.version` and
  `geomaster.extraction.grounding_rejections`. Its session id is the scan that enqueued it (REQ-19).

## Data model

Flyway `V6__extraction_analysis.sql`:

```sql
brand            (id, account_id, project_id, kind own|competitor, name, created_at, deleted_at)
                 UNIQUE (project_id) WHERE kind='own' AND deleted_at IS NULL
brand_alias      (id, account_id, project_id, brand_id, kind name|domain, value, normalized, created_at, deleted_at)
                 UNIQUE (project_id, normalized) WHERE deleted_at IS NULL
answer_extraction(id, llm_call_id → llm_call, extractor_version, status queued|running|done|failed,
                  status_reason, attempts, next_attempt_at, locked_until, model, model_version,
                  input_tokens, output_tokens, cost_chf numeric(12,6), price_version, latency_ms,
                  grounding_rejections, created_at, finished_at,
                  UNIQUE (llm_call_id, extractor_version))
answer_brand     (extraction_id, ordinal, name, normalized, domain, sentiment, recommended,
                  evidence, position, PRIMARY KEY (extraction_id, ordinal))
scan_mention     (account_id, scan_id, scan_unit_id, extraction_id, ordinal → answer_brand,
                  brand_id NULL = untracked, extracted_name, position, sentiment, recommended,
                  resolver_version, PRIMARY KEY (scan_unit_id, ordinal),
                  UNIQUE (scan_unit_id, brand_id) WHERE brand_id IS NOT NULL)
scan_citation    (account_id, scan_id, scan_unit_id, url, domain, source provider|text,
                  class own|competitor|third_party, brand_id NULL, resolver_version,
                  PRIMARY KEY (scan_unit_id, url))
scan             + analysed_at, extraction_cost_chf, answers_extracted, extractions_failed,
                   grounding_rejections
```

- **Shared rows.** `answer_extraction` and `answer_brand` hang off the shared `llm_call`, like
  `llm_call_citation`, and carry no tenant data. Every tenant-owned table (`brand`, `brand_alias`,
  `scan_mention`, `scan_citation`) has `account_id`, and every query filters by it.
- **Deletes.** Brand and alias deletes are soft, so past `scan_mention` rows keep their `brand_id`
  (REQ-3).

## Error handling and idempotency

- **Idempotent enqueue.** The unique constraint makes enqueueing safe to repeat.
- **Safe worker crashes.** A crashed worker's lease expires and the row is claimed again. Results
  are written in one transaction (extraction row and `answer_brand` rows), so a retry never
  duplicates them.
- **Retryable failures:** timeouts, 429, 5xx, `stop_reason = max_tokens`. These retry with backoff
  up to `geomaster.extractor.max-attempts`.
- **Terminal failures:**
  - `stop_reason = refusal`: Haiku 5.5 has no server-side fallback, so it is terminal with reason
    `refused`.
  - Schema-invalid JSON: terminal with reason `invalid_output`.
- **Effect of failures.** Failed extractions count as "answer without data": the scan is still
  analysed, and `geo-score` excludes them from denominators and shows the count.
- **Resolution** is a pure function of (extractions, brand set, resolver version). For each scan it
  deletes and re-inserts that scan's `scan_mention` / `scan_citation` rows in one transaction.

## Cost and limits

| Item | Value |
| --- | --- |
| Model | Claude Haiku 5.5: USD 0.10 / 1M input, 0.50 / 1M output (prompts ≤ 100K tokens) |
| Typical extraction | ~1.2K system (cached prefix) + ~1.5K answer in, ~400 out ≈ USD 0.0005 |
| Share of the per-answer budget | ≈ 1 % of the CHF 0.05 budget. Cross-account reuse divides it further |
| Prompt caching | The system prompt is a stable prefix with `cache_control`. If it stays under the model's minimum cacheable length, it simply isn't cached (no error) |
| Batch API | −50 %. Deferred: it adds latency that manual scans would feel, for cents a month |
| Limits | `geomaster.extractor.max-tokens`, `.concurrency` and `.max-attempts`. The budget guard counts extraction cost in the monthly forecast |

Competitor limits are read from `plans.json` `competitorsPerDomain` (ADR-0010). `null` means no
limit.

## Testing strategy (tests, evals, Lighthouse where relevant)

- **Unit:**
  - `BrandNormalizer` with a table of variants, e.g. "Ride Flumserberg", "rideflumserberg.ch",
    "RIDE-Flumserberg AG", "Skischule Flumserberg GmbH" ≠ "Ride Flumserberg".
  - `GroundingCheck`, `PositionLocator` and `UrlScanner`.
  - The `ExtractorVersion` fingerprint pin.
- **Integration** (Testcontainers, `FakeAdapter.extractStructured`):
  - Enqueue on unit done, plus reuse on a cache hit.
  - Retry, refusal and invalid-output paths.
  - Analysis ends with the scan analysed and cost added.
  - Re-resolution after an alias edit.
  - Account isolation on the brands and mentions APIs.
  - Competitor limit per plan.
- **Eval suite** (`apps/core/src/eval/`, Gradle task `:core:extractorEval`):
  - Dataset `extractor/dataset-v1.jsonl`, at least 30 answers per language. Answers are real scan
    answers, including dogfooding rideflumserberg.ch. Labels are drafted by a stronger model and
    reviewed by the owner.
  - Each label lists the brands (canonical name plus accepted variants), their sentiment and their
    order.
  - Scoring:
    - Micro precision and recall on (answer, brand) after resolution, overall and per language.
    - Position exact-match.
    - Sentiment accuracy.
  - Gate: precision ≥ 0.95 and recall ≥ 0.90 overall. Per-language figures and position and
    sentiment accuracy are reported and tracked, not gating, until a beta baseline exists.
  - The report (JSON + Markdown) is uploaded as a CI artifact. It records the dataset hash,
    extractor version, cost and duration.
  - Cost per run ≈ 120 × USD 0.0005 ≈ USD 0.06.
- **CI:**
  - The `extractor-eval` job in `ci-core.yml` runs when paths under `extractor/`, the eval dataset,
    or `geomaster.extractor.*` config change, and weekly on a schedule.
  - It needs the `ANTHROPIC_API_KEY` repo secret.
  - It is a required check (REQ-22). The job always starts so the check never hangs as "expected";
    a `git diff` step against the base decides whether the paid eval runs or the job passes as
    skipped.
