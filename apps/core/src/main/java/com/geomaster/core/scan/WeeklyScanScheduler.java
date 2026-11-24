package com.geomaster.core.scan;

import com.geomaster.core.plans.Plans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Starts the weekly scans (scan-engine REQ-5, T10). Each run picks the active projects on a
 * {@code weekly} plan that have prompts, no queued or running scan, and no non-failed scan in the last
 * {@value ScanPlanner#WEEKLY_INTERVAL_DAYS} days, oldest last scan first, and starts each through
 * {@link ScanPlanner} — so frequency, one-active-scan and the budget guard are enforced exactly as for a
 * manual start.
 *
 * <p>A transaction-scoped Postgres advisory lock makes sure only one core instance schedules at a time;
 * another instance that runs concurrently skips. Every project starts in its own transaction, so one
 * refusal (budget, no engines) never rolls back the others. The cron lives in
 * {@link ScanSchedulerConfig}; tests call {@link #runOnce()} directly.
 */
@Component
public class WeeklyScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyScanScheduler.class);

    /** Advisory lock key shared by all core instances ("geo_scan" in ASCII). */
    static final long LOCK_KEY = 0x67656f5f7363616eL;
    /** Upper bound per run; the next hourly run picks up the rest. */
    static final int BATCH = 500;

    public record Result(boolean locked, int started, int refused) {
        static Result skipped() {
            return new Result(false, 0, 0);
        }
    }

    private record Due(UUID account, UUID project) {}

    private final JdbcClient db;
    private final Plans plans;
    private final ScanPlanner planner;
    private final TransactionTemplate outer;
    private final TransactionTemplate perProject;

    public WeeklyScanScheduler(JdbcClient db, Plans plans, ScanPlanner planner, PlatformTransactionManager tx) {
        this.db = db;
        this.plans = plans;
        this.planner = planner;
        this.outer = new TransactionTemplate(tx);
        this.perProject = new TransactionTemplate(tx);
        this.perProject.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** One scheduling pass; returns what it did (skipped when another instance holds the lock). */
    public Result runOnce() {
        List<String> weekly = plans.idsWithScanFrequency("weekly");
        if (weekly.isEmpty()) {
            return new Result(true, 0, 0);
        }
        Result result = outer.execute(status -> {
            boolean locked = db.sql("SELECT pg_try_advisory_xact_lock(:k)").param("k", LOCK_KEY)
                    .query(Boolean.class).single();
            if (!locked) {
                return Result.skipped();
            }
            int started = 0;
            int refused = 0;
            for (Due due : due(weekly)) {
                if (start(due)) {
                    started++;
                } else {
                    refused++;
                }
            }
            return new Result(true, started, refused);
        });
        if (result != null && (result.started() > 0 || result.refused() > 0)) {
            log.info("Weekly scheduler: {} scans started, {} refused", result.started(), result.refused());
        }
        return result == null ? Result.skipped() : result;
    }

    private boolean start(Due due) {
        try {
            perProject.executeWithoutResult(s -> planner.start(due.account(), due.project(), ScanPlanner.Trigger.SCHEDULE));
            return true;
        } catch (ScanRefusedException e) {
            log.debug("Weekly scan of project {} refused: {} ({})", due.project(), e.reason(), e.getMessage());
            return false;
        } catch (RuntimeException e) {
            log.warn("Weekly scan of project {} failed to start", due.project(), e);
            return false;
        }
    }

    private List<Due> due(List<String> weeklyPlans) {
        return db.sql("""
                SELECT p.account_id, p.id
                FROM project p
                JOIN account a ON a.id = p.account_id
                WHERE p.status = 'active'
                  AND a.plan_id = ANY(CAST(:plans AS text[]))
                  AND EXISTS (SELECT 1 FROM prompt q
                              WHERE q.project_id = p.id AND q.account_id = p.account_id AND q.deleted_at IS NULL)
                  AND NOT EXISTS (SELECT 1 FROM scan s
                                  WHERE s.project_id = p.id AND s.account_id = p.account_id
                                    AND (s.status IN ('queued', 'running')
                                         OR (s.status <> 'failed'
                                             AND s.created_at > now() - make_interval(days => :days))))
                ORDER BY (SELECT max(s.created_at) FROM scan s
                          WHERE s.project_id = p.id AND s.account_id = p.account_id) ASC NULLS FIRST,
                         p.created_at, p.id
                LIMIT :batch
                """)
                .param("plans", weeklyPlans.toArray(String[]::new))
                .param("days", ScanPlanner.WEEKLY_INTERVAL_DAYS)
                .param("batch", BATCH)
                .query((rs, i) -> new Due(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                .list();
    }
}
