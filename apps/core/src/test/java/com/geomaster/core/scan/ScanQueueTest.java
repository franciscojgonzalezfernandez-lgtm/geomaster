package com.geomaster.core.scan;

import com.geomaster.core.TestcontainersConfiguration;
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
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scan_unit queue end to end with fake engines (scan-engine T4). */
@Import({TestcontainersConfiguration.class, ScanQueueTest.Engines.class})
@SpringBootTest(properties = {
        "geomaster.scan.worker.enabled=false",          // the test drives the queue
        "geomaster.scan.worker.max-attempts=3",
        "geomaster.scan.worker.backoff-base=0s",
        "geomaster.scan.worker.breaker-threshold=1000",
        "geomaster.providers.engines.openai.enabled=true",
        "geomaster.providers.engines.openai.model=fake-openai",
        "geomaster.providers.engines.openai.prices.version=test",
        "geomaster.providers.engines.openai.prices.input-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.output-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.per-thousand-searches-usd=10",
        "geomaster.providers.engines.gemini.enabled=true",
        "geomaster.providers.engines.gemini.model=fake-gemini",
        "geomaster.providers.engines.gemini.prices.version=test",
        "geomaster.providers.engines.gemini.prices.input-per-million-usd=1",
        "geomaster.providers.engines.gemini.prices.output-per-million-usd=1",
        "geomaster.providers.engines.gemini.prices.per-thousand-searches-usd=14"})
class ScanQueueTest {

    /** Per-engine behaviour the tests can swap; counts real (non-cached) calls. */
    static final Map<Engine, Function<AnswerRequest, ProviderAnswer>> BEHAVIOUR = new ConcurrentHashMap<>();
    static final Map<Engine, AtomicInteger> CALLS = new ConcurrentHashMap<>();

    @TestConfiguration
    static class Engines {
        @Bean
        ProviderAdapter fakeOpenAi() {
            return adapter(Engine.OPENAI);
        }

        @Bean
        ProviderAdapter fakeGemini() {
            return adapter(Engine.GEMINI);
        }

        private static ProviderAdapter adapter(Engine e) {
            return new FakeAdapter(e, req -> {
                CALLS.computeIfAbsent(e, k -> new AtomicInteger()).incrementAndGet();
                return BEHAVIOUR.getOrDefault(e, r -> FakeAdapter.defaultAnswer(e, r)).apply(req);
            });
        }
    }

    @Autowired
    ScanWorker worker;

    @Autowired
    JdbcClient db;

    @BeforeEach
    void reset() {
        BEHAVIOUR.clear();
        CALLS.clear();
        db.sql("DELETE FROM scan_unit").update();
        db.sql("DELETE FROM llm_call").update();
    }

    record Fixture(UUID account, UUID scan, UUID prompt) {}

    Fixture fixture(String promptText) {
        UUID account = db.sql("INSERT INTO account (name) VALUES ('q') RETURNING id").query(UUID.class).single();
        UUID project = db.sql("INSERT INTO project (account_id, domain, country, region) VALUES (:a, :d, 'CH', 'SG') RETURNING id")
                .param("a", account).param("d", UUID.randomUUID() + ".ch").query(UUID.class).single();
        UUID prompt = db.sql("INSERT INTO prompt (account_id, project_id, locale, topic) VALUES (:a, :p, 'de', 'reviews') RETURNING id")
                .param("a", account).param("p", project).query(UUID.class).single();
        db.sql("INSERT INTO prompt_version (prompt_id, version, account_id, text) VALUES (:pr, 1, :a, :t)")
                .param("pr", prompt).param("a", account).param("t", promptText).update();
        UUID scan = db.sql("INSERT INTO scan (account_id, project_id, trigger, samples) VALUES (:a, :p, 'manual', 1) RETURNING id")
                .param("a", account).param("p", project).query(UUID.class).single();
        return new Fixture(account, scan, prompt);
    }

    void unit(Fixture f, Engine engine, int sample) {
        db.sql("""
                INSERT INTO scan_unit (account_id, scan_id, prompt_id, prompt_version, engine, locale, sample)
                VALUES (:a, :s, :p, 1, :e, 'de', :n)
                """).param("a", f.account()).param("s", f.scan()).param("p", f.prompt())
                .param("e", engine.id()).param("n", sample).update();
    }

    String status(Fixture f, Engine engine) {
        return db.sql("SELECT string_agg(status, ',' ORDER BY sample) FROM scan_unit WHERE scan_id = :s AND engine = :e")
                .param("s", f.scan()).param("e", engine.id()).query(String.class).single();
    }

    void drainUntilIdle() {
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

    @Test
    void successStoresCallCitationsAndCostAndMarksScanRunning() {
        Fixture f = fixture("Beste Snowboardschule Flumserberg?");
        unit(f, Engine.OPENAI, 1);
        drainUntilIdle();

        assertEquals("done", status(f, Engine.OPENAI));
        var row = db.sql("""
                SELECT c.engine, c.model, c.country, c.region, c.search_count, c.cost_chf, c.price_version,
                       (SELECT count(*) FROM llm_call_citation WHERE llm_call_id = c.id) AS citations,
                       s.status, s.units_done, s.cost_chf AS scan_cost
                FROM scan_unit u JOIN llm_call c ON c.id = u.llm_call_id JOIN scan s ON s.id = u.scan_id
                WHERE u.scan_id = :s
                """).param("s", f.scan()).query().singleRow();
        assertEquals("openai", row.get("engine"));
        assertEquals("CH", ((String) row.get("country")).trim());
        assertEquals("SG", row.get("region"));
        assertEquals(1L, ((Number) row.get("citations")).longValue());
        assertEquals("test", row.get("price_version"));
        assertTrue(((java.math.BigDecimal) row.get("cost_chf")).signum() > 0, "real cost recorded (FR-SCN-06)");
        assertEquals("running", row.get("status"));
        assertEquals(1, ((Number) row.get("units_done")).intValue());
        assertEquals(0, ((java.math.BigDecimal) row.get("scan_cost")).compareTo((java.math.BigDecimal) row.get("cost_chf")));
    }

    @Test
    void sameQuestionSameDayIsAskedOnceAcrossAccounts() {
        Fixture a = fixture("Snowboardkurs Flumserberg?");
        Fixture b = fixture("Snowboardkurs Flumserberg?");
        unit(a, Engine.OPENAI, 1);
        unit(b, Engine.OPENAI, 1);
        drainUntilIdle();

        assertEquals(1, CALLS.get(Engine.OPENAI).get(), "shared cache (FR-SCN-04)");
        int hits = db.sql("SELECT count(*) FROM scan_unit WHERE cache_hit AND cost_chf = 0").query(Integer.class).single();
        assertEquals(1, hits);
    }

    @Test
    void retryableErrorsRetryThenSucceedWithoutRepeatingDoneUnits() {
        Fixture f = fixture("Q retry");
        unit(f, Engine.OPENAI, 1);
        unit(f, Engine.OPENAI, 2);
        AtomicInteger failures = new AtomicInteger(1); // the first call fails
        BEHAVIOUR.put(Engine.OPENAI, req -> {
            if (failures.getAndDecrement() > 0) {
                throw ProviderException.fromStatus(503, "overloaded", null);
            }
            return FakeAdapter.defaultAnswer(Engine.OPENAI, new AnswerRequest(req.promptText() + "#" + CALLS.get(Engine.OPENAI).get(),
                    req.locale(), req.country(), req.region(), req.maxSearches()));
        });
        drainUntilIdle();

        assertEquals("done,done", status(f, Engine.OPENAI));
        int attempts = db.sql("SELECT sum(attempts) FROM scan_unit WHERE scan_id = :s").param("s", f.scan()).query(Integer.class).single();
        assertEquals(3, attempts, "one retry; the done unit was never run again");
        assertEquals(0, worker.drainOnce(Engine.OPENAI), "nothing left to run");
    }

    @Test
    void attemptsExhaustedOrPermanentErrorFailTheUnit() {
        Fixture f = fixture("Q fail");
        unit(f, Engine.OPENAI, 1);
        BEHAVIOUR.put(Engine.OPENAI, req -> { throw ProviderException.fromStatus(500, "down", null); });
        drainUntilIdle();
        assertEquals("failed", status(f, Engine.OPENAI));
        assertEquals(3, db.sql("SELECT attempts FROM scan_unit WHERE scan_id = :s").param("s", f.scan()).query(Integer.class).single());

        Fixture g = fixture("Q bad request");
        unit(g, Engine.OPENAI, 1);
        BEHAVIOUR.put(Engine.OPENAI, req -> { throw ProviderException.fromStatus(400, "bad", null); });
        drainUntilIdle();
        assertEquals("failed", status(g, Engine.OPENAI));
        assertEquals(1, db.sql("SELECT attempts FROM scan_unit WHERE scan_id = :s").param("s", g.scan()).query(Integer.class).single());
        assertEquals(1, db.sql("SELECT units_failed FROM scan WHERE id = :s").param("s", g.scan()).query(Integer.class).single());
    }

    @Test
    void oneEngineDownDoesNotBlockTheOther() {
        Fixture f = fixture("Q partial");
        unit(f, Engine.OPENAI, 1);
        unit(f, Engine.GEMINI, 1);
        BEHAVIOUR.put(Engine.GEMINI, req -> { throw ProviderException.fromStatus(503, "down", null); });
        drainUntilIdle();
        assertEquals("done", status(f, Engine.OPENAI));
        assertEquals("failed", status(f, Engine.GEMINI));
    }

    @Test
    void expiredLeaseIsReclaimedAndTheStaleWorkerCannotWrite() {
        Fixture f = fixture("Q lease");
        unit(f, Engine.OPENAI, 1);
        // Simulate a worker that claimed the unit (attempt 1) and died.
        db.sql("UPDATE scan_unit SET status = 'running', attempts = 1, locked_until = now() - interval '1 minute' WHERE scan_id = :s")
                .param("s", f.scan()).update();
        drainUntilIdle();
        assertEquals("done", status(f, Engine.OPENAI));
        assertEquals(2, db.sql("SELECT attempts FROM scan_unit WHERE scan_id = :s").param("s", f.scan()).query(Integer.class).single());
    }
}
