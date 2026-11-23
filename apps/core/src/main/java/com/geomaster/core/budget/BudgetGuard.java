package com.geomaster.core.budget;

import java.util.UUID;

/**
 * Asked before every scan starts (REQ-18, FR-OPS-02). The real policy (pause the free tool, then new
 * Free scans, never paid scans) belongs to the {@code budget-guard-backoffice} spec; until then
 * {@link AllowAllBudgetGuard} is used.
 */
public interface BudgetGuard {

    Decision allowScan(UUID accountId, String planId);

    /** {@code reason} is a stable code shown to the user when a scan is refused (e.g. {@code free_scans_paused}). */
    record Decision(boolean allowed, String reason) {

        public static Decision allow() {
            return new Decision(true, null);
        }

        public static Decision refuse(String reason) {
            return new Decision(false, reason);
        }
    }
}
