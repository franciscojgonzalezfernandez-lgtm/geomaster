package com.geomaster.core.scan;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Queue worker tuning (ADR-0004). Defaults suit a single small Fly.io machine.
 *
 * @param enabled          false = no background polling (tests drive the queue directly)
 * @param pollInterval     how often each engine's poller looks for claimable units
 * @param lease            how long a claimed unit is owned before another worker may reclaim it
 * @param maxAttempts      attempts per unit before it fails (FR-SCN-05)
 * @param backoffBase      first retry delay; doubles per attempt
 * @param breakerThreshold consecutive failures that pause an engine
 * @param breakerCooldown  how long a paused engine is skipped
 */
@ConfigurationProperties("geomaster.scan.worker")
public record ScanWorkerProperties(
        Boolean enabled,
        Duration pollInterval,
        Duration lease,
        Integer maxAttempts,
        Duration backoffBase,
        Integer breakerThreshold,
        Duration breakerCooldown) {

    public ScanWorkerProperties {
        enabled = enabled == null || enabled;
        pollInterval = pollInterval != null ? pollInterval : Duration.ofSeconds(2);
        lease = lease != null ? lease : Duration.ofMinutes(5);
        maxAttempts = maxAttempts != null ? maxAttempts : 3;
        backoffBase = backoffBase != null ? backoffBase : Duration.ofSeconds(30);
        breakerThreshold = breakerThreshold != null ? breakerThreshold : 5;
        breakerCooldown = breakerCooldown != null ? breakerCooldown : Duration.ofMinutes(5);
    }

    /** Delay before retry number {@code attempt} (1-based): base × 2^(attempt-1), capped at 1 hour. */
    public Duration backoff(int attempt) {
        long factor = 1L << Math.min(Math.max(attempt - 1, 0), 7);
        Duration d = backoffBase.multipliedBy(factor);
        return d.compareTo(Duration.ofHours(1)) > 0 ? Duration.ofHours(1) : d;
    }
}
