package com.geomaster.core.scan;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs {@link WeeklyScanScheduler} on a cron (default: minute 7 of every hour, UTC). Off with
 * {@code geomaster.scan.scheduler.enabled=false}, as in tests, which call {@code runOnce()} themselves.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "geomaster.scan.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class ScanSchedulerConfig {

    private final WeeklyScanScheduler scheduler;

    ScanSchedulerConfig(WeeklyScanScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Scheduled(cron = "${geomaster.scan.scheduler.cron:0 7 * * * *}", zone = "UTC")
    void tick() {
        scheduler.runOnce();
    }
}
