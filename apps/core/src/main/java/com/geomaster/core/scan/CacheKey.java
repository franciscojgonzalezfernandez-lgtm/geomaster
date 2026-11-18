package com.geomaster.core.scan;

import com.geomaster.core.providers.Engine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

/**
 * Shared-cache key (FR-SCN-04): the same prompt text, engine, model, language, location and UTC
 * day is asked once for all accounts. Only a hash of the text is stored.
 */
public final class CacheKey {

    private CacheKey() {}

    public static String sha256(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String of(String promptText, Engine engine, String model, String locale,
                            String country, String region, LocalDate utcDay) {
        return String.join("|",
                sha256(promptText), engine.id(), model, locale,
                country == null ? "" : country, region == null ? "" : region.trim().toLowerCase(), utcDay.toString());
    }
}
