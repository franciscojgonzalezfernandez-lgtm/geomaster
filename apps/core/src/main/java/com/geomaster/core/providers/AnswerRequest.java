package com.geomaster.core.providers;

import java.util.Objects;

/**
 * One question to one engine with web search on (FR-SCN-01).
 *
 * @param promptText  the exact prompt version text
 * @param locale      measurement language (en, de, fr, es)
 * @param country     ISO 3166-1 alpha-2 user location, or {@code null}
 * @param region      free-text region, or {@code null}
 * @param maxSearches hard cap on web searches for this call (FR-OPS-03)
 */
public record AnswerRequest(String promptText, String locale, String country, String region, int maxSearches) {
    public AnswerRequest {
        Objects.requireNonNull(promptText, "promptText");
        Objects.requireNonNull(locale, "locale");
        if (maxSearches < 0) {
            throw new IllegalArgumentException("maxSearches must be >= 0");
        }
    }
}
