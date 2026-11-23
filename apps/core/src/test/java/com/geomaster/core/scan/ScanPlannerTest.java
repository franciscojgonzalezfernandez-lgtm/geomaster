package com.geomaster.core.scan;

import com.geomaster.core.TestcontainersConfiguration;
import com.geomaster.core.api.NotFoundException;
import com.geomaster.core.budget.BudgetGuard;
import com.geomaster.core.providers.AnswerRequest;
import com.geomaster.core.providers.Engine;
import com.geomaster.core.providers.FakeAdapter;
import com.geomaster.core.providers.ProviderAdapter;
import com.geomaster.core.providers.ProviderAnswer;
import com.geomaster.core.providers.ProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.geomaster.core.scan.ScanPlanner.Trigger.MANUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Scan planning, budget gate and finalization end to end with fake engines (scan-engine T5). */
@Import({TestcontainersConfiguration.class, ScanPlannerTest.Fakes.class})
@SpringBootTest(properties = {
        "geomaster.providers.real-adapters=false",
        "geomaster.scan.worker.enabled=false",          // the test drives the queue
        "geomaster.scan.worker.max-attempts=1",
        "geomaster.scan.worker.backoff-base=0s",
        "geomaster.scan.worker.breaker-threshold=1000",
        "geomaster.providers.engines.openai.enabled=true",
        "geomaster.providers.engines.openai.model=fake-openai",
        "geomaster.providers.engines.openai.prices.version=p-openai",
        "geomaster.providers.engines.openai.prices.input-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.output-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.per-thousand-searches-usd=10",
        "geomaster.providers.engines.gemini.enabled=true",
        "geomaster.providers.engines.gemini.model=fake-gemini",
        "geomaster.providers.engines.gemini.prices.version=p-gemini",
        "geomaster.providers.engines.gemini.prices.input-per-million-usd=1",
        "geomaster.providers.engines.gemini.prices.output-per-million-usd=1",
        "geomaster.providers.engines.gemini.prices.per-thousand-searches-usd=14",
        "geomaster.providers.engines.claude.enabled=false"})
class ScanPlannerTest {

    static final Map<Engine, Function<AnswerRequest, ProviderAnswer>> BEHAVIOUR = new ConcurrentHashMap<>();
    /** The budget decision the next scan gets; reset to "allow" before each test. */
    static final AtomicReference<BudgetGuard.Decision> BUDGET = new AtomicReference<>(BudgetGuard.Decision.allow());

    @TestConfiguration
    static class Fakes {
        @Bean
        ProviderAdapter fakeOpenAi() {
            return adapter(Engine.OPENAI);
        }

        @Bean
        ProviderAdapter fakeGemini() {
            return adapter(Engine.GEMINI);
        }

        @Bean
        @Primary
        BudgetGuard switchableBudgetGuard() {
            return (account, plan) -> BUDGET.get();
        }

        private static ProviderAdapter adapter(Engine e) {
            return new FakeAdapter(e, req -> BEHAVIOUR.getOrDefault(e, r -> FakeAdapter.defaultAnswer(e, r)).apply(req));
        }
    }

    @Autowired
    ScanPlanner planner;

    @Autowired
    ScanWorker worker;

    @Autowired
    JdbcClient db;

    @BeforeEach
    void reset() {
        BEHAVIOUR.clear();
        BUDGET.set(BudgetGuard.Decision.allow());
        db.sql("DELETE FROM scan_unit").update();
        db.sql("DELETE FROM llm_call").update();
    }

    record Project(UUID account, UUID id) {}

    /** An account on {@code plan} with one project measuring {@code locales}. */
    Project project(String plan, String... locales) {
        UUID account = db.sql("INSERT INTO account (name, plan_id) VALUES ('t5', :plan) RETURNING id")
                .param("plan", plan).query(UUID.class).single();
        UUID project = db.sql("INSERT INTO project (account_id, domain, country) VALUES (:a, :d, 'CH') RETURNING id")
                .param("a", account).param("d", UUID.randomUUID() + ".ch").query(UUID.class).single();
        for (String l : locales) {
            db.sql("INSERT INTO project_language (project_id, locale) VALUES (:p, :l)")
                    .param("p", project).param("l", l).update();
        }
        return new Project(account, project);
    }

    UUID prompt(Project p, String locale, String text) {
        UUID id = db.sql("INSERT INTO prompt (account_id, project_id, locale, topic) VALUES (:a, :p, :l, 'reviews') RETURNING id")
                .param("a", p.account()).param("p", p.id()).param("l", locale).query(UUID.class).single();
        db.sql("INSERT INTO prompt_version (prompt_id, version, account_id, text) VALUES (:id, 1, :a, :t)")
                .param("id", id).param("a", p.account()).param("t", text + " " + UUID.randomUUID()).update();
        return id;
    }

    Map<String, Object> scan(UUID id) {
        return db.sql("SELECT status, samples, units_total, units_done, units_failed, versions::text AS versions, finished_at FROM scan WHERE id = :id")
                .param("id", id).query().singleRow();
    }

    List<String> unitEngines(UUID scan) {
        return db.sql("SELECT DISTINCT engine FROM scan_unit WHERE scan_id = :s ORDER BY engine")
                .param("s", scan).query(String.class).list();
    }

    void drain() {
        for (int i = 0; i < 20; i++) {
            int n = 0;
            for (Engine e : worker.engines()) {
                n += worker.drainOnce(e);
            }
            if (n == 0) {
                return;
            }
        }
    }

    ScanRefusedException.Reason refusal(Project p) {
        return assertThrows(ScanRefusedException.class, () -> planner.start(p.account(), p.id(), MANUAL)).reason();
    }

    @Test
    void freePlanAsksOnlyChatGptWithTwoBaselineSamples() {
        Project p = project("free", "de");
        prompt(p, "de", "Beste Snowboardschule Flumserberg?");
        prompt(p, "de", "Snowboardkurs Preise Flumserberg?");

        ScanPlan plan = planner.start(p.account(), p.id(), MANUAL);

        assertEquals(List.of(Engine.OPENAI), plan.engines(), "Free = ChatGPT only (REQ-3)");
        assertEquals(2, plan.samples(), "first scan = baseline (REQ-4)");
        assertEquals(4, plan.units());
        assertEquals(List.of("openai"), unitEngines(plan.scanId()));
        Map<String, Object> s = scan(plan.scanId());
        assertEquals("queued", s.get("status"));
        assertEquals(4, ((Number) s.get("units_total")).intValue());
        assertTrue(((String) s.get("versions")).contains("fake-openai"), "models recorded (NFR-13)");
        assertTrue(((String) s.get("versions")).contains("p-openai"), "price version recorded (REQ-14)");
    }

    @Test
    void enginesAndLanguagesFollowThePlanAndConfiguration() {
        // Starter: 3 engines, 2 languages. Claude is disabled in config, so only two engines run.
        Project p = project("starter", "en", "de", "fr");
        prompt(p, "en", "Best snowboard school?");
        prompt(p, "de", "Beste Snowboardschule?");
        prompt(p, "fr", "Meilleure école de snowboard ?");   // 3rd language: over the plan's limit

        ScanPlan plan = planner.start(p.account(), p.id(), MANUAL);

        assertEquals(List.of(Engine.OPENAI, Engine.GEMINI), plan.engines());
        assertEquals(List.of("en", "de"), plan.locales());
        assertEquals(2, plan.prompts());
        assertEquals(2 * 2 * 2, plan.units(), "prompts × engines × samples");
    }

    @Test
    void deletedPromptsAreNotAskedAndAnEmptyProjectIsRefused() {
        Project p = project("starter", "de");
        UUID gone = prompt(p, "de", "Alt");
        db.sql("UPDATE prompt SET deleted_at = now() WHERE id = :id").param("id", gone).update();
        assertEquals(ScanRefusedException.Reason.NO_PROMPTS, refusal(p));

        prompt(p, "de", "Neu");
        assertEquals(1, planner.start(p.account(), p.id(), MANUAL).prompts());
    }

    @Test
    void oneActiveScanPerProject() {
        Project p = project("starter", "de");
        prompt(p, "de", "Q");
        planner.start(p.account(), p.id(), MANUAL);
        assertEquals(ScanRefusedException.Reason.ALREADY_ACTIVE, refusal(p), "REQ-6");
    }

    @Test
    void freePlanGetsExactlyOneScanButAFailedOneDoesNotCount() {
        Project p = project("free", "de");
        prompt(p, "de", "Q once");
        BEHAVIOUR.put(Engine.OPENAI, r -> { throw ProviderException.fromStatus(400, "bad", null); });
        UUID first = planner.start(p.account(), p.id(), MANUAL).scanId();
        drain();
        assertEquals("failed", scan(first).get("status"));

        BEHAVIOUR.clear();
        UUID second = planner.start(p.account(), p.id(), MANUAL).scanId();
        drain();
        assertEquals("completed", scan(second).get("status"));
        assertEquals(ScanRefusedException.Reason.FREQUENCY, refusal(p), "REQ-5: once");
    }

    @Test
    void weeklyPlanAllowsOneScanPerSevenDaysAndLaterScansUseOneSample() {
        Project p = project("starter", "de");
        prompt(p, "de", "Q weekly");
        UUID first = planner.start(p.account(), p.id(), MANUAL).scanId();
        drain();
        assertEquals("completed", scan(first).get("status"));
        assertEquals(ScanRefusedException.Reason.FREQUENCY, refusal(p), "REQ-5: weekly");

        db.sql("UPDATE scan SET created_at = now() - interval '7 days 1 minute' WHERE id = :id").param("id", first).update();
        ScanPlan next = planner.start(p.account(), p.id(), ScanPlanner.Trigger.SCHEDULE);
        assertEquals(1, next.samples(), "baseline only on the first scan (REQ-4)");
    }

    @Test
    void budgetGuardCanRefuse() {
        Project p = project("free", "de");
        prompt(p, "de", "Q budget");
        BUDGET.set(BudgetGuard.Decision.refuse("free_scans_paused"));
        ScanRefusedException e = assertThrows(ScanRefusedException.class, () -> planner.start(p.account(), p.id(), MANUAL));
        assertEquals(ScanRefusedException.Reason.BUDGET, e.reason());
        assertEquals("free_scans_paused", e.getMessage(), "REQ-18: says why");
        assertEquals(0, db.sql("SELECT count(*) FROM scan WHERE project_id = :p").param("p", p.id()).query(Integer.class).single());
    }

    @Test
    void pausedProjectsAndOtherAccountsProjectsAreNotScanned() {
        Project p = project("starter", "de");
        prompt(p, "de", "Q");
        Project other = project("starter", "de");
        assertThrows(NotFoundException.class, () -> planner.start(other.account(), p.id(), MANUAL), "tenant isolation");

        db.sql("UPDATE project SET status = 'paused' WHERE id = :p").param("p", p.id()).update();
        assertEquals(ScanRefusedException.Reason.PROJECT_INACTIVE, refusal(p));
    }

    @Test
    void scanCompletesWhenAllUnitsAnswer() {
        Project p = project("starter", "de");
        prompt(p, "de", "Q complete");
        UUID id = planner.start(p.account(), p.id(), MANUAL).scanId();
        drain();
        Map<String, Object> s = scan(id);
        assertEquals("completed", s.get("status"), "REQ-15");
        assertEquals(4, ((Number) s.get("units_done")).intValue());
        assertTrue(s.get("finished_at") != null);
    }

    @Test
    void oneProviderDownGivesAPartialScanWithTheOthersAnswered() {
        Project p = project("starter", "de");
        prompt(p, "de", "Q partial");
        BEHAVIOUR.put(Engine.GEMINI, r -> { throw ProviderException.fromStatus(503, "down", null); });
        UUID id = planner.start(p.account(), p.id(), MANUAL).scanId();
        drain();
        Map<String, Object> s = scan(id);
        assertEquals("partial", s.get("status"), "NFR-02 / REQ-10");
        assertEquals(2, ((Number) s.get("units_done")).intValue());
        assertEquals(2, ((Number) s.get("units_failed")).intValue());
    }
}
