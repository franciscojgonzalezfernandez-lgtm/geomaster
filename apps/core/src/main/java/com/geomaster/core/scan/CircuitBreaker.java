package com.geomaster.core.scan;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Pauses one engine after N consecutive failures so a provider outage does not burn retries; the
 * other engines keep working (NFR-02). Thread-safe.
 */
public final class CircuitBreaker {

    private final int threshold;
    private final Duration cooldown;
    private final Clock clock;
    private int consecutiveFailures;
    private Instant openUntil = Instant.EPOCH;

    public CircuitBreaker(int threshold, Duration cooldown, Clock clock) {
        this.threshold = threshold;
        this.cooldown = cooldown;
        this.clock = clock;
    }

    public synchronized boolean allows() {
        return !clock.instant().isBefore(openUntil);
    }

    public synchronized void success() {
        consecutiveFailures = 0;
    }

    public synchronized void failure() {
        if (++consecutiveFailures >= threshold) {
            openUntil = clock.instant().plus(cooldown);
            consecutiveFailures = 0;
        }
    }
}
