package com.geomaster.core.providers;

/**
 * The only path from core to an LLM provider (ADR-0007, CLAUDE.md boundary). One implementation per
 * engine over its official SDK; swapping a model is configuration (NFR-12). Implementations must be
 * thread-safe: queue workers call them concurrently on virtual threads.
 */
public interface ProviderAdapter {

    Engine engine();

    /** Ask with web search on; throws {@link ProviderException} on failure. */
    ProviderAnswer answerWithSearch(AnswerRequest request);

    /**
     * Schema-constrained structured output without tools (extraction-analysis REQ-5). Only adapters
     * whose provider serves the extractor implement it; throws {@link ProviderException} on failure.
     */
    default StructuredResult extractStructured(StructuredRequest request) {
        throw new UnsupportedOperationException(engine().id() + " does not serve structured extraction");
    }
}
