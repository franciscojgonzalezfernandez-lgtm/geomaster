package com.geomaster.core.extraction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The extractor version recorded on every extraction (REQ-17) and the fingerprint of everything that
 * shapes its output: prompt, instruction, schema, model, effort, thinking and max tokens. A unit test
 * pins fingerprint → version, so changing any input without bumping {@code version} fails the build.
 */
public record ExtractorVersion(String version, String fingerprint) {

    public static ExtractorVersion of(ExtractorProperties props, ExtractorPrompt prompt) {
        return new ExtractorVersion(props.version(), fingerprint(props, prompt));
    }

    static String fingerprint(ExtractorProperties props, ExtractorPrompt prompt) {
        String material = String.join("\u0000",
                prompt.system(), prompt.instruction(), prompt.schemaJson(),
                props.model(), props.effort(), props.thinking(), Integer.toString(props.maxTokens()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
