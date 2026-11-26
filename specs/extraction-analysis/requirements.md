# extraction-analysis — Requirements

Traces to: FR-EXT-01..03, FR-ONB-02 (minimal), FR-DSH-04 (data), NFR-04, NFR-10, NFR-12, NFR-13
Constrained by: ADR-0005 (Flyway), ADR-0007 (ProviderAdapter `extractStructured`), ADR-0010 (plan limits), ADR-0012 (observability), ADR-0013 (extractor evals in CI)
Phase: B (FR-EXT-04 gap analysis with RAG is L and out of scope here)

The extractor turns each literal answer stored by `scan-engine` into data: which brands it names,
in what order, with what sentiment, and which domains and URLs it cites. Entity resolution then maps
those names to the project's own brand and its competitors. Everything the GEO Score, dashboard and
recommendations show is computed from this output, so its quality is gated by a versioned eval suite
(precision ≥ 95 %, recall ≥ 90 %) and every result records the extractor version that produced it.

## User stories

- As a user, I want to know whether each AI answer mentions my brand, in which position and in what tone, so that I can trust my GEO Score.
- As a user, I want to name my brand's variants and my competitors (within my plan's limit) so that "Ride Flumserberg" and "rideflumserberg.ch" count as the same brand.
- As a user, I want to see which brands the AIs name that I do not track yet, so that I discover competitors.
- As the owner, I want every extraction's model, version, tokens and cost recorded, and an eval suite that blocks a worse extractor, so that metrics stay reproducible and cheap.

## Acceptance criteria (EARS)

### Brands (minimal, before the onboarding agent exists)
- REQ-1: WHEN a project is created, THE SYSTEM SHALL create its own brand with the project domain as a domain alias and a display name derived from the domain; the user may rename it and add name or domain aliases.
- REQ-2: WHEN a user adds a competitor (name plus optional domain and aliases), THE SYSTEM SHALL store it, and reject it with 422 if the project's competitor count would exceed the plan's `competitorsPerDomain` limit from `plans.json` or if its name or domain resolves to a brand already in the project.
- REQ-3: WHEN a user deletes a competitor or an alias, THE SYSTEM SHALL stop resolving to it from then on and keep past resolved mentions with the resolver version they were computed with (NFR-13).

### Extraction
- REQ-4: WHEN a scan unit reaches `done`, THE SYSTEM SHALL ensure exactly one extraction exists for its answer (`llm_call`) and the current extractor version; answers reused from the shared cache (FR-SCN-04) SHALL reuse the existing extraction instead of extracting again.
- REQ-5: WHEN an extraction runs, THE SYSTEM SHALL ask the configured extractor model through the ProviderAdapter layer for schema-constrained structured output and SHALL record, for each brand named in the answer: its name as written, the domain if the answer states one, its sentiment (`positive`, `neutral`, `negative`), whether the answer recommends it, and a short literal evidence quote (FR-EXT-01).
- REQ-6: THE SYSTEM SHALL extract brands without knowing which project asked, so one extraction serves every account that shares the answer.
- REQ-7: WHEN the model returns a brand whose name or evidence quote does not appear in the answer text (after whitespace and case normalisation), THE SYSTEM SHALL drop that brand and count it as a grounding rejection.
- REQ-8: THE SYSTEM SHALL compute each brand's position as the 1-based order of its first occurrence in the answer text, not from the model's output order.
- REQ-9: THE SYSTEM SHALL take cited URLs and domains from the provider citations already stored with the answer, plus URLs that appear literally in the answer text, without an LLM call.
- REQ-10: WHEN an extraction fails with a retryable error (timeout, 429, 5xx, output cut off by the token limit), THE SYSTEM SHALL retry it with backoff up to the configured attempts; IF the model refuses or the output still fails schema validation, THEN THE SYSTEM SHALL mark the extraction `failed` with the reason and not retry.
- REQ-11: THE SYSTEM SHALL treat answer text as untrusted data: it is passed as content to extract from, never as instructions, and only schema-valid output is stored.

### Entity resolution
- REQ-12: WHEN a scan's answers are all extracted (or failed), THE SYSTEM SHALL resolve each extracted brand to the project's own brand, one of its competitors, or `untracked`, by comparing normalised names, aliases and domain stems (case, accents, punctuation, legal suffixes such as AG, GmbH, SA, Sàrl, S.L., Ltd, Inc, and TLDs removed) (FR-EXT-02).
- REQ-13: WHEN two extracted names in one answer resolve to the same brand, THE SYSTEM SHALL count one mention at the earliest position.
- REQ-14: THE SYSTEM SHALL classify every cited domain of the answer as own (matches the own brand's domains), competitor, or third party.
- REQ-15: WHEN a user changes a project's brands or aliases, THE SYSTEM SHALL re-resolve that project's stored extractions without calling the model again.

### Recording and versions
- REQ-16: FOR every extraction call THE SYSTEM SHALL store model, model version, input and output tokens, latency and real cost in CHF from configured prices with their price version (NFR-04), and SHALL add extraction cost to the scan's cost totals.
- REQ-17: THE SYSTEM SHALL record the extractor version on every extraction and the extractor and resolver versions in the scan's `versions` (NFR-13); a change to the extractor prompt, schema, model or parameters SHALL change the version.
- REQ-18: WHEN a scan's extraction and resolution finish, THE SYSTEM SHALL mark the scan analysed with counts of answers extracted, failed and grounding rejections.
- REQ-19: THE SYSTEM SHALL emit a span per extraction call with gen_ai attributes and cost, and never export answer text unless content capture is enabled (ADR-0012).

### Eval suite
- REQ-20: THE SYSTEM SHALL keep a versioned, labelled dataset of real answers covering EN, DE, FR and ES, with the expected brands, their sentiment and the expected order.
- REQ-21: WHEN the eval runs, THE SYSTEM SHALL report brand-mention precision and recall (per answer × brand, after resolution) overall and per language, position accuracy and sentiment accuracy, and SHALL fail if overall precision < 0.95 or recall < 0.90 (FR-EXT-03).
- REQ-22: WHEN a pull request changes the extractor prompt, schema, model, parameters, resolver or dataset, THE SYSTEM SHALL run the eval in CI as a required check before merge (ADR-0013, ADR-0019).

### API
- REQ-23: WHEN a user reads a project's brands, THE SYSTEM SHALL return own brand, competitors and aliases, account-scoped (404 for other accounts); add, edit and delete follow REQ-1..REQ-3.
- REQ-24: WHEN a user reads a scan's mentions, THE SYSTEM SHALL return, per answer, the resolved brands with position, sentiment, recommended flag and evidence, plus untracked brands and the cited domains with their class, account-scoped.

## Out of scope
- Competitor suggestions and brand inference by the onboarding agent (`onboarding-agent`); here they are entered by hand.
- GEO Score axes and confidence intervals (`geo-score`); this spec stores the inputs.
- Dashboard views (`dashboard`); the project page only gets a minimal brands editor.
- Gap analysis with RAG (FR-EXT-04, launch phase).
- Fuzzy matching that merges brands automatically; near matches are only offered as alias suggestions later.
- Batch API for extraction (a cost lever once volumes justify it).
