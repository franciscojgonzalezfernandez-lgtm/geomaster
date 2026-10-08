# ADR-0007: Provider layer: own ProviderAdapter over official Java SDKs

- Status: Accepted
- Date: 2026-10-08
- Deciders: Javi
- Related requirements: FR-SCN-01, FR-SCN-06, NFR-04, NFR-12, NFR-13

## Context

The three engines expose web search differently (OpenAI Responses `web_search`, Gemini Google Search grounding, Claude web search tool) and return citations and usage in different shapes. Models must be swappable by configuration.

## Options considered

| Option | Pros | Cons |
| --- | --- | --- |
| Own interface over official SDKs | Exact control of search tools, citations, usage and cost | More code than a generic framework |
| Spring AI as the core abstraction | Less code | Lowest common denominator over provider-specific search features |

## Decision

`ProviderAdapter` interface in `providers` with `answerWithSearch(prompt, locale, location)` returning text, citations, search count, token usage, cost and model version, plus `extractStructured(...)`. One implementation per provider over its official Java SDK. Models and prices live in configuration. Spring AI may be used inside an adapter where it helps.

## Consequences

Every call is recorded for cost and reproducibility. Adding an engine = one adapter + configuration.
