package com.geomaster.core.prompts;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An active prompt with its current version's text. */
public record Prompt(
        UUID id,
        UUID projectId,
        String locale,
        String topic,
        int currentVersion,
        String text,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
