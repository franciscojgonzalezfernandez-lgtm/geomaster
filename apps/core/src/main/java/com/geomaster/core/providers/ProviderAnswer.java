package com.geomaster.core.providers;

import java.time.Duration;
import java.util.List;

/**
 * What an engine answered, before cost is applied. Cost is computed by {@link CostCalculator} from
 * configured prices (REQ-14), so adapters never carry prices.
 *
 * @param searchSuggestionsHtml Gemini's Google Search Suggestions (search entry point) to show
 *                              next to the answer (FR-SCN-07); {@code null} for other engines
 */
public record ProviderAnswer(
        String text,
        List<Citation> citations,
        List<String> searchQueries,
        int searchCount,
        Usage usage,
        String model,
        String modelVersion,
        String searchSuggestionsHtml,
        Duration latency) {

    public record Citation(String url, String title) {}

    public record Usage(long inputTokens, long outputTokens) {}

    public ProviderAnswer {
        citations = List.copyOf(citations);
        searchQueries = List.copyOf(searchQueries);
    }
}
