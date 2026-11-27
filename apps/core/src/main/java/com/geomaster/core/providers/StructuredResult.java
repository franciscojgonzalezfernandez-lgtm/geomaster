package com.geomaster.core.providers;

import java.time.Duration;

/**
 * Raw result of a schema-constrained call: the JSON text as returned (validated by the caller),
 * why the model stopped, and what to record (REQ-16).
 *
 * @param stopReason {@code end_turn}, {@code max_tokens}, {@code refusal}, …
 */
public record StructuredResult(
        String json,
        String stopReason,
        String requestedModel,
        String model,
        long inputTokens,
        long outputTokens,
        Duration latency) {

    public static final String END_TURN = "end_turn";
    public static final String MAX_TOKENS = "max_tokens";
    public static final String REFUSAL = "refusal";
}
