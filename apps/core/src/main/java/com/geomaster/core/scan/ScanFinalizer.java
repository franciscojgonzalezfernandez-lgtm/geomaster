package com.geomaster.core.scan;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Closes a scan once none of its units is pending or running (REQ-15): {@code completed} when every
 * unit answered, {@code partial} when some failed (NFR-02: one provider down never blocks the
 * others), {@code failed} when none answered. Totals (units, answers, cost) are kept up to date by
 * the queue as units finish; this sets the final status and {@code finished_at}.
 *
 * <p>Safe to call concurrently and repeatedly: it is a single conditional UPDATE that only applies
 * to a scan still queued/running with no open units. Each worker calls it after committing its
 * unit, so the last unit to finish always sees all the others' results.
 */
@Repository
public class ScanFinalizer {

    private final JdbcClient db;

    public ScanFinalizer(JdbcClient db) {
        this.db = db;
    }

    /** @return the final status if this call closed the scan, otherwise empty */
    public Optional<String> finalizeIfDone(UUID scanId, UUID accountId) {
        return db.sql("""
                UPDATE scan s
                SET status = CASE
                        WHEN s.units_failed = 0 THEN 'completed'
                        WHEN s.units_done > 0 THEN 'partial'
                        ELSE 'failed' END,
                    status_reason = CASE
                        WHEN s.units_failed = 0 THEN NULL
                        ELSE s.units_failed || ' of ' || s.units_total || ' units failed' END,
                    started_at = coalesce(s.started_at, now()),
                    finished_at = now()
                WHERE s.id = :scan AND s.account_id = :account
                  AND s.status IN ('queued', 'running')
                  AND NOT EXISTS (SELECT 1 FROM scan_unit u
                                  WHERE u.scan_id = s.id AND u.status IN ('pending', 'running'))
                RETURNING s.status
                """)
                .param("scan", scanId).param("account", accountId)
                .query(String.class).optional();
    }
}
