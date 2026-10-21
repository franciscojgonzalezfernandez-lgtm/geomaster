package com.geomaster.core.accounts;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A measured domain owned by one {@link Account}. Measurement languages are held separately in
 *  {@code project_language} and read via {@link ProjectRepository#languagesOf(UUID)}. */
public record Project(
        UUID id,
        UUID accountId,
        String domain,
        String country,
        String region,
        String status,
        OffsetDateTime createdAt) {}
