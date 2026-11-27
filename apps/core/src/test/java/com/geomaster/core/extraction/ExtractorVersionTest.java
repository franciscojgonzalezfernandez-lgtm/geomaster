package com.geomaster.core.extraction;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins extractor version → fingerprint (REQ-17). If this fails, the prompt, schema, model, effort,
 * thinking or max tokens changed: bump {@code geomaster.extractor.version} and ADD a line here.
 * Never edit an existing line: stored extractions and eval reports refer to it.
 */
class ExtractorVersionTest {

    static final Map<String, String> PINS = Map.of(
            "ext-1", "385c0fdd92222cfaf6ec2086e01fbf1b7d4cf6c67d48f13ba766a911de3e8ddf");

    @Test
    void configuredVersionMatchesItsPinnedFingerprint() throws Exception {
        Properties p = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            p.load(in);
        }
        ExtractorProperties props = new ExtractorProperties(false, p.getProperty("geomaster.extractor.version"),
                defaultOf(p.getProperty("geomaster.extractor.model")), null,
                p.getProperty("geomaster.extractor.effort"), p.getProperty("geomaster.extractor.thinking"),
                Integer.valueOf(p.getProperty("geomaster.extractor.max-tokens")), Duration.ofSeconds(60), null, null);
        ExtractorVersion v = ExtractorVersion.of(props, ExtractorPrompt.v1());
        String pinned = PINS.get(v.version());
        assertNotNull(pinned, "no pin for " + v.version() + "; add: \"" + v.version() + "\", \"" + v.fingerprint() + "\"");
        assertEquals(pinned, v.fingerprint(), "extractor inputs changed: bump geomaster.extractor.version and add a pin");
    }

    /** {@code ${EXTRACTOR_MODEL:claude-haiku-5-5}} → {@code claude-haiku-5-5}. */
    static String defaultOf(String value) {
        return value.startsWith("${") ? value.substring(value.indexOf(':') + 1, value.length() - 1) : value;
    }
}
