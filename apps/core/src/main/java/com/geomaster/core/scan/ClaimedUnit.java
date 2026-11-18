package com.geomaster.core.scan;

import com.geomaster.core.providers.Engine;

import java.util.UUID;

/**
 * A unit a worker owns until its lease expires. {@code attempt} is the fencing token: a late
 * worker whose lease was reclaimed can no longer write this unit's result.
 */
public record ClaimedUnit(
        UUID id,
        UUID accountId,
        UUID scanId,
        Engine engine,
        String locale,
        int attempt,
        String promptText,
        String country,
        String region) {}
