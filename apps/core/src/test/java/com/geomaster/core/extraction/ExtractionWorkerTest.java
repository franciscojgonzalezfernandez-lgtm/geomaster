package com.geomaster.core.extraction;

import com.geomaster.core.TestcontainersConfiguration;
import com.geomaster.core.observability.ScanTelemetry;
import com.geomaster.core.providers.AnswerRequest;
import com.geomaster.core.providers.CostCalculator;
import com.geomaster.core.providers.Engine;
import com.geomaster.core.providers.FakeAdapter;
import com.geomaster.core.providers.ProviderAdapter;
import com.geomaster.core.providers.ProviderAnswer;
import com.geomaster.core.providers.ProviderException;
import com.geomaster.core.providers.StructuredRequest;
import com.geomaster.core.providers.StructuredResult;
import com.geomaster.core.scan.ScanWorker;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Extraction queue and scan analysis end to end with a fake engine and a fake extractor model (extraction-analysis T5, T6). */
@Import({TestcontainersConfiguration.class, ExtractionWorkerTest.Fakes.class})
@SpringBootTest(properties = {
        "geomaster.providers.real-adapters=false",
        "geomaster.scan.worker.enabled=false",          // the test drives both queues
        "geomaster.scan.worker.max-attempts=3",
        "geomaster.scan.worker.backoff-base=0s",
        "geomaster.scan.worker.breaker-threshold=1000",
        "geomaster.providers.engines.openai.enabled=true",
        "geomaster.providers.engines.openai.model=fake-openai",
        "geomaster.providers.engines.openai.prices.version=test",
        "geomaster.providers.engines.openai.prices.input-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.output-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.per-thousand-searches-usd=10",
        "geomaster.providers.engines.gemini.enabled=false",
        "geomaster.providers.engines.claude.enabled=false"})
class ExtractionWorkerTest {

    static final String ANSWER = "Für Snowboardkurse empfehle ich Ride Flumserberg. Die Skischule Flumserberg ist oft ausgebucht. Mehr auf www.ride-flumserberg.ch/kurse.";
    static final String OUTPUT = """
            {"brands":[
              {"name":"Skischule Flumserberg","domain":null,"sentiment":"neutral","recommended":false,
               "evidence":"Die Skischule Flumserberg ist oft ausgebucht"},
              {"name":"Ride Flumserberg","domain":null,"sentiment":"positive","recommended":true,
               "evidence":"empfehle ich Ride Flumserberg"},
              {"name":"Snowboard Heroes","domain":null,"sentiment":"positive","recommended":true,
               "evidence":"Snowboard Heroes ist super"}]}
            """;
    static final InMemorySpanExporter SPANS = InMemorySpanExporter.create();
    /** Scripted extractor replies, one per call; when empty the call returns {@link #OUTPUT}. */
    static final Deque<Function<StructuredRequest, StructuredResult>> SCRIPT = new ArrayDeque<>();

    static StructuredResult reply(String json, String stop) {
        return new StructuredResult(json, stop, "claude-haiku-5-5", "claude-haiku-5-5-20261101", 1000, 200, Duration.ofMillis(9));
    }

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        OpenTelemetry testOpenTelemetry() {
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(SPANS)).build())
                    .build();
        }

        @Bean
        ProviderAdapter fakeOpenAi() {
            return new FakeAdapter(Engine.OPENAI, r -> answer(r));
        }

        @Bean
        Extractor testExtractor(CostCalculator costs) {
            ProviderAdapter claude = new ProviderAdapter() {
                @Override
                public Engine engine() {
                    return Engine.CLAUDE;
                }

                @Override
                public ProviderAnswer answerWithSearch(AnswerRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public StructuredResult extractStructured(StructuredRequest request) {
                    Function<StructuredRequest, StructuredResult> next;
                    synchronized (SCRIPT) {
                        next = SCRIPT.poll();
                    }
                    return next == null ? reply(OUTPUT, StructuredResult.END_TURN) : next.apply(request);
                }
            };
            return new Extractor(claude, ExtractorTest.PROPS, ExtractorPrompt.v1(), costs);
        }

        static ProviderAnswer answer(AnswerRequest r) {
            ProviderAnswer d = FakeAdapter.defaultAnswer(Engine.OPENAI, r);
            return new ProviderAnswer(ANSWER, d.citations(), d.searchQueries(), d.searchCount(), d.usage(),
                    d.model(), d.modelVersion(), null, d.latency());
        }
    }

    @Autowired
    ScanWorker scans;

    @Autowired
    ExtractionWorker extractions;

    @Autowired
    JdbcClient db;

    @BeforeEach
    void reset() {
        SPANS.reset();
        SCRIPT.clear();
        db.sql("DELETE FROM scan_unit").update();
        db.sql("DELETE FROM llm_call").update(); // cascades to answer_extraction and answer_brand
    }

    record Fixture(UUID account, UUID scan, UUID prompt) {}

    Fixture fixture(String promptText) {
        UUID account = db.sql("INSERT INTO account (name) VALUES ('x') RETURNING id").query(UUID.class).single();
        UUID project = db.sql("INSERT INTO project (account_id, domain, country) VALUES (:a, :d, 'CH') RETURNING id")
                .param("a", account).param("d", UUID.randomUUID() + ".ch").query(UUID.class).single();
        UUID prompt = db.sql("INSERT INTO prompt (account_id, project_id, locale, topic) VALUES (:a, :p, 'de', 'reviews') RETURNING id")
                .param("a", account).param("p", project).query(UUID.class).single();
        db.sql("INSERT INTO prompt_version (prompt_id, version, account_id, text) VALUES (:pr, 1, :a, :t)")
                .param("pr", prompt).param("a", account).param("t", promptText).update();
        UUID scan = db.sql("INSERT INTO scan (account_id, project_id, trigger, samples) VALUES (:a, :p, 'manual', 1) RETURNING id")
                .param("a", account).param("p", project).query(UUID.class).single();
        db.sql("""
                INSERT INTO scan_unit (account_id, scan_id, prompt_id, prompt_version, engine, locale, sample)
                VALUES (:a, :s, :p, 1, 'openai', 'de', 1)
                """).param("a", account).param("s", scan).param("p", prompt).update();
        return new Fixture(account, scan, prompt);
    }

    void drainScans() {
        while (scans.drainOnce(Engine.OPENAI) > 0) { }
    }

    void drainExtractions() {
        for (int i = 0; i < 10 && extractions.drainOnce() > 0; i++) { }
    }

    Map<String, Object> extraction() {
        return db.sql("""
                SELECT status, status_reason, attempts, extractor_version, model, model_version, input_tokens,
                       output_tokens, cost_chf, price_version, latency_ms, grounding_rejections, finished_at
                FROM answer_extraction
                """).query().singleRow();
    }

    @Test
    void doneUnitQueuesOneExtractionAndCacheHitsReuseIt() {
        String q = "Snowboardschule Flumserberg " + UUID.randomUUID();
        fixture(q);
        drainScans();
        assertEquals(1, count("answer_extraction"));
        assertEquals("queued", extraction().get("status"));

        fixture(q); // another account asks the same question: shared cache hit (FR-SCN-04)
        drainScans();
        assertEquals(1, db.sql("SELECT count(*) FROM scan_unit WHERE cache_hit").query(Integer.class).single());
        assertEquals(1, count("answer_extraction"), "a cache hit reuses the extraction (REQ-4)");
    }

    @Test
    void groundedBrandsAreStoredWithUsageCostAndSpan() {
        Fixture f = fixture("Snowboard " + UUID.randomUUID());
        drainScans();
        drainExtractions();

        Map<String, Object> x = extraction();
        assertEquals("done", x.get("status"));
        assertEquals("ext-1", x.get("extractor_version"));
        assertEquals("claude-haiku-5-5", x.get("model"));
        assertEquals("claude-haiku-5-5-20261101", x.get("model_version"));
        assertEquals(1000, x.get("input_tokens"));
        assertEquals(200, x.get("output_tokens"));
        // (1000 × 0.10 + 200 × 0.50) / 1e6 USD × 0.80 = 0.00016 CHF
        assertEquals(0, new BigDecimal("0.000160").compareTo((BigDecimal) x.get("cost_chf")));
        assertEquals("anthropic-test", x.get("price_version"));
        assertEquals(9, x.get("latency_ms"));
        assertEquals(1, x.get("grounding_rejections"), "Snowboard Heroes is not in the answer (REQ-7)");

        List<String> brands = db.sql("""
                SELECT position || ':' || name || ':' || normalized || ':' || ordinal FROM answer_brand ORDER BY position
                """).query(String.class).list();
        assertEquals(List.of("1:Ride Flumserberg:rideflumserberg:2", "2:Skischule Flumserberg:skischuleflumserberg:1"), brands);

        SpanData span = SPANS.getFinishedSpanItems().stream().filter(s -> s.getName().equals("chat claude-haiku-5-5"))
                .findFirst().orElseThrow();
        assertEquals("chat", span.getAttributes().get(AttributeKey.stringKey("gen_ai.operation.name")));
        assertEquals(f.scan().toString(), span.getAttributes().get(AttributeKey.stringKey("langfuse.session.id")));
        assertEquals("ext-1", span.getAttributes().get(AttributeKey.stringKey("geomaster.extractor.version")));
        assertEquals(1L, span.getAttributes().get(AttributeKey.longKey("geomaster.extraction.grounding_rejections")));
        assertEquals(1000L, span.getAttributes().get(AttributeKey.longKey("gen_ai.usage.input_tokens")));
        assertTrue(span.getAttributes().get(AttributeKey.doubleKey("geomaster.cost_chf")) > 0);
        assertNull(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.input")), "no answer text by default (REQ-19)");
        assertNull(span.getAttributes().get(AttributeKey.stringKey("langfuse.observation.output")));
        assertTrue(span.getAttributes().asMap().keySet().stream().noneMatch(k -> k.getKey().contains("account")),
                "extractions are tenant-free (REQ-6)");
    }

    @Test
    void cutOffOutputIsRetriedAndEveryCallIsBilled() {
        SCRIPT.add(r -> reply("{\"brands\":[", StructuredResult.MAX_TOKENS));
        fixture("Retry " + UUID.randomUUID());
        drainScans();
        drainExtractions();

        Map<String, Object> x = extraction();
        assertEquals("done", x.get("status"));
        assertEquals(2, ((Number) x.get("attempts")).intValue());
        assertEquals(2000, x.get("input_tokens"), "both calls counted (REQ-16)");
        assertEquals(0, new BigDecimal("0.000320").compareTo((BigDecimal) x.get("cost_chf")));
    }

    @Test
    void refusalAndInvalidOutputFailWithoutRetry() {
        SCRIPT.add(r -> reply("", StructuredResult.REFUSAL));
        fixture("Refuse " + UUID.randomUUID());
        drainScans();
        drainExtractions();
        Map<String, Object> x = extraction();
        assertEquals("failed", x.get("status"));
        assertEquals("refusal", x.get("status_reason"));
        assertEquals(1, ((Number) x.get("attempts")).intValue());
        assertEquals(1000, x.get("input_tokens"), "a refused call is still billed");
        assertEquals("claude-haiku-5-5", x.get("model"));

        reset();
        SCRIPT.add(r -> reply("{\"brands\":[{\"name\":\"A\"}]}", StructuredResult.END_TURN));
        fixture("Invalid " + UUID.randomUUID());
        drainScans();
        drainExtractions();
        assertEquals("invalid_output", extraction().get("status_reason"));
        assertEquals(0, count("answer_brand"));
    }

    @Test
    void retryableErrorsGiveUpAfterMaxAttempts() {
        for (int i = 0; i < 3; i++) {
            SCRIPT.add(r -> {
                throw ProviderException.fromStatus(529, "claude overloaded_error", null);
            });
        }
        fixture("Outage " + UUID.randomUUID());
        drainScans();
        drainExtractions();
        Map<String, Object> x = extraction();
        assertEquals("failed", x.get("status"));
        assertEquals("max_attempts", x.get("status_reason"));
        assertEquals(3, ((Number) x.get("attempts")).intValue());
        assertEquals(0, ((BigDecimal) x.get("cost_chf")).signum(), "no call was billed");
        assertTrue(SPANS.getFinishedSpanItems().stream().filter(s -> s.getName().startsWith("chat claude"))
                .allMatch(s -> "provider_retryable".equals(s.getAttributes().get(AttributeKey.stringKey("geomaster.extraction.reason")))));
    }

    UUID brand(Fixture f, String kind, String name, String domain) {
        UUID project = db.sql("SELECT project_id FROM scan WHERE id = :s").param("s", f.scan()).query(UUID.class).single();
        UUID id = db.sql("INSERT INTO brand (account_id, project_id, kind, name) VALUES (:a, :p, :k, :n) RETURNING id")
                .param("a", f.account()).param("p", project).param("k", kind).param("n", name).query(UUID.class).single();
        if (domain != null) {
            db.sql("""
                    INSERT INTO brand_alias (account_id, project_id, brand_id, kind, value, normalized)
                    VALUES (:a, :p, :b, 'domain', :v, :n)
                    """).param("a", f.account()).param("p", project).param("b", id).param("v", domain)
                    .param("n", com.geomaster.core.brands.BrandNormalizer.domain(domain)).update();
        }
        return id;
    }

    void analyse() {
        for (int i = 0; i < 10 && extractions.analyseOnce() > 0; i++) { }
    }

    Map<String, Object> scanRow(Fixture f) {
        return db.sql("""
                SELECT analysed_at, answers_extracted, extractions_failed, grounding_rejections, extraction_cost_chf,
                       cost_chf - (SELECT sum(u.cost_chf) FROM scan_unit u WHERE u.scan_id = s.id) AS added_cost,
                       brands_version, versions -> 'extractor' ->> 'version' AS extractor,
                       versions ->> 'resolver' AS resolver, versions -> 'engines' IS NOT NULL AS engines_kept
                FROM scan s WHERE id = :s
                """).param("s", f.scan()).query().singleRow();
    }

    List<String> mentions(Fixture f, UUID own, UUID rival) {
        return db.sql("SELECT position, brand_id, extracted_name FROM scan_mention WHERE scan_id = :s ORDER BY position")
                .param("s", f.scan())
                .query((rs, i) -> {
                    UUID b = rs.getObject("brand_id", UUID.class);
                    String who = b == null ? "untracked" : b.equals(own) ? "own" : b.equals(rival) ? "competitor" : "?";
                    return rs.getInt("position") + ":" + who + ":" + rs.getString("extracted_name");
                }).list();
    }

    List<String> citations(Fixture f) {
        return db.sql("SELECT domain || ':' || source || ':' || class FROM scan_citation WHERE scan_id = :s ORDER BY domain")
                .param("s", f.scan()).query(String.class).list();
    }

    @Test
    void finishedScanIsAnalysedWithMentionsCitationsCostAndVersions() {
        Fixture f = fixture("Analyse " + UUID.randomUUID());
        UUID own = brand(f, "own", "Ride Flumserberg", "ride-flumserberg.ch");
        db.sql("UPDATE scan SET versions = '{\"engines\":{}}' WHERE id = :s").param("s", f.scan()).update();
        drainScans();
        analyse();
        assertNull(scanRow(f).get("analysed_at"), "not analysed while its extraction is queued (REQ-12)");

        drainExtractions();
        analyse();
        Map<String, Object> s = scanRow(f);
        assertTrue(s.get("analysed_at") != null);
        assertEquals(1, s.get("answers_extracted"));
        assertEquals(0, s.get("extractions_failed"));
        assertEquals(1, s.get("grounding_rejections"));
        assertEquals(0, new BigDecimal("0.000160").compareTo((BigDecimal) s.get("extraction_cost_chf")));
        assertEquals(0, new BigDecimal("0.000160").compareTo((BigDecimal) s.get("added_cost")), "REQ-16");
        assertEquals("ext-1", s.get("extractor"));
        assertEquals("res-1", s.get("resolver"));
        assertEquals(true, s.get("engines_kept"), "analysis adds to the planner's versions");
        assertEquals(0, s.get("brands_version"));

        assertEquals(List.of("1:own:Ride Flumserberg", "2:untracked:Skischule Flumserberg"), mentions(f, own, null));
        assertEquals(List.of("example.com:provider:third_party", "ride-flumserberg.ch:text:own"), citations(f));

        analyse();
        assertEquals(0, new BigDecimal("0.000160").compareTo((BigDecimal) scanRow(f).get("added_cost")),
                "a second sweep never adds the cost again");
    }

    @Test
    void brandEditReResolvesWithoutCallingTheModel() {
        Fixture f = fixture("Reresolve " + UUID.randomUUID());
        UUID own = brand(f, "own", "Ride Flumserberg", "ride-flumserberg.ch");
        drainScans();
        drainExtractions();
        analyse();

        UUID rival = brand(f, "competitor", "Skischule Flumserberg", "skischule-flumserberg.ch");
        db.sql("UPDATE project SET brands_version = brands_version + 1 WHERE id = (SELECT project_id FROM scan WHERE id = :s)")
                .param("s", f.scan()).update();
        SCRIPT.add(r -> {
            throw new AssertionError("re-resolution must not call the model (REQ-15)");
        });
        analyse();

        assertEquals(List.of("1:own:Ride Flumserberg", "2:competitor:Skischule Flumserberg"), mentions(f, own, rival));
        assertEquals(1, scanRow(f).get("brands_version"));
        assertEquals(1, ((Number) extraction().get("attempts")).intValue());
        assertEquals(1, SCRIPT.size(), "the scripted model call was never used");
    }

    @Test
    void failedExtractionStillAnalysesTheScanAndClassifiesCitations() {
        SCRIPT.add(r -> reply("", StructuredResult.REFUSAL));
        Fixture f = fixture("Refused " + UUID.randomUUID());
        brand(f, "own", "Ride Flumserberg", "ride-flumserberg.ch");
        drainScans();
        drainExtractions();
        analyse();

        Map<String, Object> s = scanRow(f);
        assertEquals(0, s.get("answers_extracted"));
        assertEquals(1, s.get("extractions_failed"));
        assertEquals(0, new BigDecimal("0.000160").compareTo((BigDecimal) s.get("added_cost")), "a refusal was billed");
        assertEquals(0, count("scan_mention WHERE scan_id = '" + f.scan() + "'"));
        assertEquals(2, citations(f).size(), "citations need no model (REQ-9, REQ-14)");
    }

    int count(String table) {
        return db.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }
}
