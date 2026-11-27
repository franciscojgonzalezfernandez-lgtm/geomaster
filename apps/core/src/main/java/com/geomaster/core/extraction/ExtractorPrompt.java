package com.geomaster.core.extraction;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Prompt v1 and schema v1 of the extractor, loaded from the classpath (REQ-5, REQ-11). */
public record ExtractorPrompt(String system, String instruction, String schemaJson, Map<String, Object> schema) {

    /** Trusted user text after the answer document. */
    static final String INSTRUCTION_V1 =
            "Extract the brands named in the answer document above, following the rules. Reply with JSON only.";

    public static ExtractorPrompt v1() {
        String schemaJson = resource("extractor/schema-v1.json");
        @SuppressWarnings("unchecked") // a JSON object always maps to Map<String, Object>
        Map<String, Object> schema = JsonMapper.builder().build().readValue(schemaJson, Map.class);
        return new ExtractorPrompt(resource("extractor/prompt-v1.txt"), INSTRUCTION_V1, schemaJson, schema);
    }

    private static String resource(String path) {
        try (InputStream in = ExtractorPrompt.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
