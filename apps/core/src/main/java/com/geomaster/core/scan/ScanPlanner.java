package com.geomaster.core.scan;

import com.geomaster.core.api.NotFoundException;
import com.geomaster.core.budget.BudgetGuard;
import com.geomaster.core.plans.Plan;
import com.geomaster.core.plans.Plans;
import com.geomaster.core.providers.Engine;
import com.geomaster.core.providers.ProvidersProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Starts a scan (REQ-3..REQ-6, REQ-18): checks the budget guard, the one-active-scan rule and the
 * plan's scan frequency, then fans the project's active prompts out into one {@code scan_unit} per
 * prompt × engine × language × sample, gated by the plan in {@code plans.json}. Everything happens in
 * one transaction under a lock on the account row, so two concurrent starts cannot both pass.
 *
 * <p>Called by the scans API (manual, T6) and the weekly scheduler (T10) with an explicit account id,
 * never from the request's tenant context alone, so the scheduler can use it too.
 */
@Service
public class ScanPlanner {

    /** Engine priority: a 1-engine plan measures ChatGPT (Free = ChatGPT, PRD). */
    static final List<Engine> ENGINE_ORDER = List.of(Engine.OPENAI, Engine.GEMINI, Engine.CLAUDE);
    /** Canonical language order, used when a downgraded plan allows fewer languages than a project has. */
    static final List<String> LOCALE_ORDER = List.of("en", "de", "fr", "es");
    static final int BASELINE_SAMPLES = 2;
    static final int WEEKLY_INTERVAL_DAYS = 7;

    public enum Trigger { MANUAL, SCHEDULE }

    private final JdbcClient db;
    private final Plans plans;
    private final ProvidersProperties providers;
    private final BudgetGuard budget;
    private final JsonMapper json = JsonMapper.builder().build();

    public ScanPlanner(JdbcClient db, Plans plans, ProvidersProperties providers, BudgetGuard budget) {
        this.db = db;
        this.plans = plans;
        this.providers = providers;
        this.budget = budget;
    }

    /**
     * Start a scan of {@code projectId} for {@code accountId}.
     *
     * @throws NotFoundException     the project does not exist in this account
     * @throws ScanRefusedException  the scan may not start; the reason says why
     */
    @Transactional
    public ScanPlan start(UUID accountId, UUID projectId, Trigger trigger) {
        String planId = db.sql("SELECT plan_id FROM account WHERE id = :a FOR UPDATE")
                .param("a", accountId).query(String.class).optional()
                .orElseThrow(() -> new NotFoundException("account"));
        Plan plan = plans.get(planId);

        String projectStatus = db.sql("SELECT status FROM project WHERE id = :p AND account_id = :a")
                .param("p", projectId).param("a", accountId).query(String.class).optional()
                .orElseThrow(() -> new NotFoundException("project"));
        if (!"active".equals(projectStatus)) {
            throw new ScanRefusedException(ScanRefusedException.Reason.PROJECT_INACTIVE, "project is " + projectStatus);
        }

        BudgetGuard.Decision decision = budget.allowScan(accountId, planId);
        if (!decision.allowed()) {
            throw new ScanRefusedException(ScanRefusedException.Reason.BUDGET, decision.reason());
        }

        requireNoActiveScan(accountId, projectId);
        requireFrequency(accountId, projectId, plan);

        List<Engine> engines = engines(plan);
        if (engines.isEmpty()) {
            throw new ScanRefusedException(ScanRefusedException.Reason.NO_ENGINES,
                    "no engine allowed by plan " + planId + " is enabled");
        }
        List<String> locales = locales(projectId, accountId, plan);
        List<UUID> prompts = prompts(projectId, accountId, locales, plan);
        if (prompts.isEmpty()) {
            throw new ScanRefusedException(ScanRefusedException.Reason.NO_PROMPTS,
                    "no active prompts in " + locales);
        }

        int samples = isFirstScan(accountId, projectId) ? BASELINE_SAMPLES : 1;
        int units = prompts.size() * engines.size() * samples;
        UUID scanId;
        try {
            scanId = db.sql("""
                    INSERT INTO scan (account_id, project_id, trigger, samples, units_total, versions)
                    VALUES (:a, :p, :t, :n, :u, CAST(:v AS jsonb))
                    RETURNING id
                    """)
                    .param("a", accountId).param("p", projectId)
                    .param("t", trigger.name().toLowerCase())
                    .param("n", samples).param("u", units).param("v", versions(engines))
                    .query(UUID.class).single();
        } catch (DuplicateKeyException e) {
            // uq_scan_one_active_per_project: the database is the last line of defence (REQ-6).
            throw new ScanRefusedException(ScanRefusedException.Reason.ALREADY_ACTIVE, "a scan is already queued or running");
        }

        int inserted = db.sql("""
                INSERT INTO scan_unit (account_id, scan_id, prompt_id, prompt_version, engine, locale, sample)
                SELECT :a, :s, p.id, p.current_version, e.engine, p.locale, g.sample
                FROM prompt p
                CROSS JOIN unnest(CAST(:engines AS text[])) AS e(engine)
                CROSS JOIN generate_series(1, :samples) AS g(sample)
                WHERE p.account_id = :a AND p.id = ANY(CAST(:ids AS uuid[]))
                """)
                .param("a", accountId).param("s", scanId)
                .param("engines", engines.stream().map(Engine::id).toArray(String[]::new))
                .param("samples", samples)
                .param("ids", prompts.toArray(UUID[]::new))
                .update();
        if (inserted != units) {
            throw new IllegalStateException("planned " + units + " units but inserted " + inserted);
        }
        return new ScanPlan(scanId, engines, locales, prompts.size(), samples, units);
    }

    private void requireNoActiveScan(UUID accountId, UUID projectId) {
        boolean active = db.sql("""
                SELECT EXISTS (SELECT 1 FROM scan WHERE project_id = :p AND account_id = :a
                               AND status IN ('queued', 'running'))
                """).param("p", projectId).param("a", accountId).query(Boolean.class).single();
        if (active) {
            throw new ScanRefusedException(ScanRefusedException.Reason.ALREADY_ACTIVE, "a scan is already queued or running");
        }
    }

    /** REQ-5: `once` → one scan per project ever; `weekly` → one per 7 days. Failed scans do not count. */
    private void requireFrequency(UUID accountId, UUID projectId, Plan plan) {
        String window = "once".equals(plan.scanFrequency())
                ? ""
                : " AND created_at > now() - make_interval(days => " + WEEKLY_INTERVAL_DAYS + ")";
        boolean used = db.sql("SELECT EXISTS (SELECT 1 FROM scan WHERE project_id = :p AND account_id = :a"
                        + " AND status <> 'failed'" + window + ")")
                .param("p", projectId).param("a", accountId).query(Boolean.class).single();
        if (used) {
            throw new ScanRefusedException(ScanRefusedException.Reason.FREQUENCY,
                    "once".equals(plan.scanFrequency())
                            ? "plan " + plan.id() + " includes one scan per project"
                            : "one scan per project every " + WEEKLY_INTERVAL_DAYS + " days");
        }
    }

    /** The first N engines in priority order (N from the plan) that are enabled in configuration. */
    List<Engine> engines(Plan plan) {
        List<Engine> out = new ArrayList<>();
        for (Engine e : ENGINE_ORDER.subList(0, Math.min(plan.engines(), ENGINE_ORDER.size()))) {
            ProvidersProperties.EngineProperties cfg = providers.engines().get(e.id());
            if (cfg != null && cfg.enabled()) {
                out.add(e);
            }
        }
        return out;
    }

    private List<String> locales(UUID projectId, UUID accountId, Plan plan) {
        List<String> projectLocales = db.sql("""
                SELECT l.locale FROM project_language l JOIN project p ON p.id = l.project_id
                WHERE p.id = :p AND p.account_id = :a
                """).param("p", projectId).param("a", accountId).query(String.class).list();
        return LOCALE_ORDER.stream().filter(projectLocales::contains).limit(plan.languages()).toList();
    }

    /** Active prompts in the measured languages, oldest first, capped at the plan's prompt limit. */
    private List<UUID> prompts(UUID projectId, UUID accountId, List<String> locales, Plan plan) {
        if (locales.isEmpty()) {
            return List.of();
        }
        return db.sql("""
                SELECT id FROM prompt
                WHERE project_id = :p AND account_id = :a AND deleted_at IS NULL
                  AND locale = ANY(CAST(:locales AS text[]))
                ORDER BY created_at, id
                LIMIT :limit
                """)
                .param("p", projectId).param("a", accountId)
                .param("locales", locales.toArray(String[]::new))
                .param("limit", plan.prompts() == null ? Integer.MAX_VALUE : plan.prompts())
                .query(UUID.class).list();
    }

    /** REQ-4: the baseline (2 samples) until the project has a scan with answers. */
    private boolean isFirstScan(UUID accountId, UUID projectId) {
        return !db.sql("""
                SELECT EXISTS (SELECT 1 FROM scan WHERE project_id = :p AND account_id = :a
                               AND status IN ('completed', 'partial'))
                """).param("p", projectId).param("a", accountId).query(Boolean.class).single();
    }

    /** NFR-13: the models and price versions this scan uses; formula/extractor slots are filled later. */
    private String versions(List<Engine> engines) {
        Map<String, Object> models = new LinkedHashMap<>();
        for (Engine e : engines) {
            ProvidersProperties.EngineProperties cfg = providers.engine(e);
            models.put(e.id(), Map.of("model", cfg.model(), "priceVersion", cfg.prices().version()));
        }
        return json.writeValueAsString(Map.of("engines", models));
    }
}
