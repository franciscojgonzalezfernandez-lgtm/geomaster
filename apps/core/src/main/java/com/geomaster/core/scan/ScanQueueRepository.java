package com.geomaster.core.scan;

import com.geomaster.core.providers.Engine;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The {@code scan_unit} work queue (ADR-0004). This is system code, not a tenant request: units are
 * claimed across accounts, and every write afterwards is keyed by the unit's id AND its
 * {@code account_id}, so a result can only land on the unit (and scan) it belongs to.
 */
@Repository
public class ScanQueueRepository {

    private final JdbcClient db;

    public ScanQueueRepository(JdbcClient db) {
        this.db = db;
    }

    /**
     * Claim up to {@code limit} runnable units of one engine with {@code FOR UPDATE SKIP LOCKED}:
     * pending units whose backoff has passed, and running units whose lease expired (crashed worker).
     */
    @Transactional
    public List<ClaimedUnit> claim(Engine engine, int limit, Duration lease) {
        List<ClaimedUnit> units = db.sql("""
                WITH claimable AS (
                    SELECT id FROM scan_unit
                    WHERE engine = :engine
                      AND ((status = 'pending' AND next_attempt_at <= now())
                        OR (status = 'running' AND locked_until < now()))
                    ORDER BY next_attempt_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                ), claimed AS (
                    UPDATE scan_unit u
                    SET status = 'running',
                        locked_until = now() + make_interval(secs => :leaseSeconds),
                        attempts = u.attempts + 1
                    FROM claimable c
                    WHERE u.id = c.id
                    RETURNING u.id, u.account_id, u.scan_id, u.prompt_id, u.prompt_version, u.engine, u.locale, u.attempts
                )
                SELECT c.id, c.account_id, c.scan_id, c.engine, c.locale, c.attempts AS attempt,
                       v.text AS prompt_text, p.country, p.region
                FROM claimed c
                JOIN prompt_version v ON v.prompt_id = c.prompt_id AND v.version = c.prompt_version
                                      AND v.account_id = c.account_id
                JOIN scan s ON s.id = c.scan_id AND s.account_id = c.account_id
                JOIN project p ON p.id = s.project_id AND p.account_id = c.account_id
                """)
                .param("engine", engine.id())
                .param("limit", limit)
                .param("leaseSeconds", (double) lease.toSeconds())
                .query((rs, i) -> new ClaimedUnit(
                        rs.getObject("id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        rs.getObject("scan_id", UUID.class),
                        Engine.fromId(rs.getString("engine")),
                        rs.getString("locale"),
                        rs.getInt("attempt"),
                        rs.getString("prompt_text"),
                        rs.getString("country"),
                        rs.getString("region")))
                .list();
        for (UUID scanId : units.stream().map(ClaimedUnit::scanId).distinct().toList()) {
            db.sql("UPDATE scan SET status = 'running', started_at = coalesce(started_at, now()) WHERE id = :id AND status = 'queued'")
                    .param("id", scanId).update();
        }
        return units;
    }

    /** Record success; ignored (returns false) when the lease was lost to another worker. */
    @Transactional
    public boolean markDone(ClaimedUnit u, UUID llmCallId, boolean cacheHit, BigDecimal costChf) {
        int n = db.sql("""
                UPDATE scan_unit
                SET status = 'done', llm_call_id = :call, cache_hit = :hit, cost_chf = :cost,
                    locked_until = NULL, last_error = NULL, finished_at = now()
                WHERE id = :id AND account_id = :account AND status = 'running' AND attempts = :attempt
                """)
                .param("call", llmCallId).param("hit", cacheHit).param("cost", costChf)
                .param("id", u.id()).param("account", u.accountId()).param("attempt", u.attempt())
                .update();
        if (n == 1) {
            db.sql("""
                    UPDATE scan SET units_done = units_done + 1, cost_chf = cost_chf + :cost
                    WHERE id = :scan AND account_id = :account
                    """)
                    .param("cost", costChf).param("scan", u.scanId()).param("account", u.accountId()).update();
        }
        return n == 1;
    }

    /** Put the unit back with a backoff (retryable failure). */
    public boolean markRetry(ClaimedUnit u, String error, Duration backoff) {
        return db.sql("""
                UPDATE scan_unit
                SET status = 'pending', locked_until = NULL, last_error = :error,
                    next_attempt_at = now() + make_interval(secs => :delay)
                WHERE id = :id AND account_id = :account AND status = 'running' AND attempts = :attempt
                """)
                .param("error", truncate(error)).param("delay", (double) backoff.toSeconds())
                .param("id", u.id()).param("account", u.accountId()).param("attempt", u.attempt())
                .update() == 1;
    }

    /** Give up on the unit (permanent error or attempts exhausted). */
    @Transactional
    public boolean markFailed(ClaimedUnit u, String error) {
        int n = db.sql("""
                UPDATE scan_unit
                SET status = 'failed', locked_until = NULL, last_error = :error, finished_at = now()
                WHERE id = :id AND account_id = :account AND status = 'running' AND attempts = :attempt
                """)
                .param("error", truncate(error))
                .param("id", u.id()).param("account", u.accountId()).param("attempt", u.attempt())
                .update();
        if (n == 1) {
            db.sql("UPDATE scan SET units_failed = units_failed + 1 WHERE id = :scan AND account_id = :account")
                    .param("scan", u.scanId()).param("account", u.accountId()).update();
        }
        return n == 1;
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
