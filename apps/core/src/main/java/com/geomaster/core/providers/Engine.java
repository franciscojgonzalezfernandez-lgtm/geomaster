package com.geomaster.core.providers;

import java.util.Locale;

/** The AI engines a scan measures (FR-SCN-01). Ids match the {@code engine} columns in the schema. */
public enum Engine {
    OPENAI, GEMINI, CLAUDE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Engine fromId(String id) {
        return valueOf(id.toUpperCase(Locale.ROOT));
    }
}
