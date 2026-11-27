package com.geomaster.core.providers;

import java.util.Map;
import java.util.Objects;

/**
 * A schema-constrained call (extraction-analysis REQ-5, ADR-0007). {@code document} is untrusted
 * data sent as a document block, never as instructions (REQ-11); {@code instruction} is the short
 * trusted user turn that follows it.
 *
 * @param model     model id sent to the provider (NFR-12)
 * @param system    fixed system prompt (cacheable)
 * @param document  content to extract from
 * @param instruction trusted text after the document
 * @param schema    JSON schema of the output, {@code additionalProperties: false} everywhere
 * @param maxTokens output cap
 * @param effort    {@code low} … {@code max}
 * @param thinking  {@code disabled} or {@code adaptive}
 */
public record StructuredRequest(
        String model,
        String system,
        String document,
        String instruction,
        Map<String, Object> schema,
        int maxTokens,
        String effort,
        String thinking) {

    public StructuredRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(instruction, "instruction");
        schema = Map.copyOf(Objects.requireNonNull(schema, "schema"));
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be > 0");
        }
    }
}
