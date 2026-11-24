package com.geomaster.core.scan;

import com.geomaster.core.TestcontainersConfiguration;
import com.geomaster.core.budget.BudgetGuard;
import com.geomaster.core.providers.Engine;
import com.geomaster.core.providers.FakeAdapter;
import com.geomaster.core.providers.ProviderAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Weekly scheduler (scan-engine T10, REQ-5): who is due, refusals isolated, one instance at a time. */
@Import({TestcontainersConfiguration.class, WeeklyScanSchedulerTest.Fakes.class})
@SpringBootTest(properties = {
        "geomaster.providers.real-adapters=false",
        "geomaster.scan.worker.enabled=false",
        "geomaster.scan.scheduler.enabled=false",
        "geomaster.providers.engines.openai.enabled=true",
        "geomaster.providers.engines.openai.model=fake-openai",
        "geomaster.providers.engines.openai.prices.version=p-openai",
        "geomaster.providers.engines.openai.prices.input-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.output-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.per-thousand-searches-usd=10",
        "geomaster.providers.engines.gemini.enabled=false",
        "geomaster.providers.engines.claude.enabled=false"})
class WeeklyScanSchedulerTest {

    /** Accounts whose scans the budget guard refuses. */
    static final Set<UUID> BLOCKED = ConcurrentHashMap.newKeySet();

    @TestConfiguration
    static class Fakes {
        @Bean
        ProviderAdapter fakeOpenAi() {
            return new FakeAdapter(Engine.OPENAI, r -> FakeAdapter.defaultAnswer(Engine.OPENAI, r));
        }

        @Bean
        @Primary
        BudgetGuard blockingBudgetGuard() {
            return (account, plan) -> BLOCKED.contains(account)
                    ? BudgetGuard.Decision.refuse("test_blocked")
                    : BudgetGuard.Decision.allow();
        }
    }

    @Autowired
    WeeklyScanScheduler scheduler;

    @Autowired
    JdbcClient db;

    @Autowired
    DataSource dataSource;

    record Project(UUID account, UUID id) {}

    @BeforeEach
    void reset() {
        BLOCKED.clear();
        // Earlier tests' projects would be due again; scope every run to this test's projects.
        db.sql("UPDATE project SET status = 'paused' WHERE status = 'active'").update();
    }

    Project project(String plan, boolean withPrompt) {
        UUID account = db.sql("INSERT INTO account (name, plan_id) VALUES ('t10', :plan) RETURNING id")
                .param("plan", plan).query(UUID.class).single();
        UUID project = db.sql("INSERT INTO project (account_id, domain, country) VALUES (:a, :d, 'CH') RETURNING id")
                .param("a", account).param("d", UUID.randomUUID() + ".ch").query(UUID.class).single();
        db.sql("INSERT INTO project_language (project_id, locale) VALUES (:p, 'de')").param("p", project).update();
        if (withPrompt) {
            UUID prompt = db.sql("INSERT INTO prompt (account_id, project_id, locale, topic) VALUES (:a, :p, 'de', 'reviews') RETURNING id")
                    .param("a", account).param("p", project).query(UUID.class).single();
            db.sql("INSERT INTO prompt_version (prompt_id, version, account_id, text) VALUES (:id, 1, :a, :t)")
                    .param("id", prompt).param("a", account).param("t", "Beste Snowboardschule? " + UUID.randomUUID()).update();
        }
        return new Project(account, project);
    }

    /** A past scan of {@code p} with {@code status}, created {@code daysAgo} days ago. */
    void pastScan(Project p, String status, int daysAgo) {
        db.sql("""
                INSERT INTO scan (account_id, project_id, trigger, samples, units_total, status, created_at, versions)
                VALUES (:a, :p, 'manual', 1, 0, :s, now() - make_interval(days => :d), '{}'::jsonb)
                """).param("a", p.account()).param("p", p.id()).param("s", status).param("d", daysAgo).update();
    }

    List<String> scheduledScans(Project p) {
        return db.sql("SELECT status FROM scan WHERE project_id = :p AND trigger = 'schedule'")
                .param("p", p.id()).query(String.class).list();
    }

    @Test
    void startsDueWeeklyProjectsOnly() {
        Project neverScanned = project("starter", true);
        Project lastWeek = project("pro", true);
        pastScan(lastWeek, "completed", 8);
        Project recent = project("starter", true);
        pastScan(recent, "partial", 3);
        Project recentFailed = project("starter", true);
        pastScan(recentFailed, "failed", 1);
        Project running = project("starter", true);
        pastScan(running, "running", 10);
        Project free = project("free", true);
        Project noPrompts = project("starter", false);

        WeeklyScanScheduler.Result r = scheduler.runOnce();

        assertTrue(r.locked());
        assertEquals(List.of("queued"), scheduledScans(neverScanned), "first weekly scan");
        assertEquals(List.of("queued"), scheduledScans(lastWeek), "a week has passed");
        assertEquals(List.of("queued"), scheduledScans(recentFailed), "failed scans do not count (REQ-5)");
        assertEquals(List.of(), scheduledScans(recent), "scanned 3 days ago");
        assertEquals(List.of(), scheduledScans(running), "one active scan per project (REQ-6)");
        assertEquals(List.of(), scheduledScans(free), "Free is `once`: never scheduled");
        assertEquals(List.of(), scheduledScans(noPrompts), "nothing to ask");
        assertEquals(3, r.started());
        assertEquals(0, r.refused());

        assertEquals(0, scheduler.runOnce().started(), "a second pass in the same week starts nothing");
    }

    @Test
    void aRefusalDoesNotStopTheOthers() {
        Project blocked = project("starter", true);
        Project ok = project("starter", true);
        BLOCKED.add(blocked.account());

        WeeklyScanScheduler.Result r = scheduler.runOnce();

        assertEquals(1, r.started());
        assertEquals(1, r.refused());
        assertEquals(List.of(), scheduledScans(blocked));
        assertEquals(List.of("queued"), scheduledScans(ok));
    }

    @Test
    void skipsWhileAnotherInstanceHoldsTheLock() throws Exception {
        Project p = project("starter", true);
        try (Connection other = dataSource.getConnection()) {
            try (PreparedStatement st = other.prepareStatement("SELECT pg_advisory_lock(?)")) {
                st.setLong(1, WeeklyScanScheduler.LOCK_KEY);
                st.execute();
            }
            WeeklyScanScheduler.Result r = scheduler.runOnce();
            assertFalse(r.locked());
            assertEquals(List.of(), scheduledScans(p));
            try (PreparedStatement st = other.prepareStatement("SELECT pg_advisory_unlock(?)")) {
                st.setLong(1, WeeklyScanScheduler.LOCK_KEY);
                st.execute();
            }
        }
        assertEquals(1, scheduler.runOnce().started(), "the lock is free again");
        assertEquals(List.of("queued"), scheduledScans(p));
    }
}
