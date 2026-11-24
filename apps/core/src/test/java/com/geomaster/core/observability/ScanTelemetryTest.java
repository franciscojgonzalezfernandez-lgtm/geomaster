package com.geomaster.core.observability;

import com.geomaster.core.TestcontainersConfiguration;
import com.geomaster.core.providers.Engine;
import com.geomaster.core.providers.FakeAdapter;
import com.geomaster.core.providers.ProviderAdapter;
import com.geomaster.core.providers.ProviderException;
import com.geomaster.core.scan.ScanPlan;
import com.geomaster.core.scan.ScanPlanner;
import com.geomaster.core.scan.ScanRefusedException;
import com.geomaster.core.scan.ScanWorker;
import io.opentelemetry.api.OpenTelemetry;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.geomaster.core.scan.ScanPlanner.Trigger.MANUAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spans per scan and per LLM call (scan-engine T11, NFR-10, ADR-0012) with fake engines. */
@Import({TestcontainersConfiguration.class, ScanTelemetryTest.Otel.class})
@SpringBootTest(properties = {
        "geomaster.providers.real-adapters=false",
        "geomaster.scan.worker.enabled=false",
        "geomaster.scan.worker.max-attempts=1",
        "geomaster.scan.worker.backoff-base=0s",
        "geomaster.scan.worker.breaker-threshold=1000",
        "geomaster.providers.engines.openai.enabled=true",
        "geomaster.providers.engines.openai.model=fake-openai",
        "geomaster.providers.engines.openai.prices.version=p-openai",
        "geomaster.providers.engines.openai.prices.input-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.output-per-million-usd=1",
        "geomaster.providers.engines.openai.prices.per-thousand-searches-usd=10",
        "geomaster.providers.engines.gemini.enabled=false",
        "geomaster.providers.engines.claude.enabled=false"})
class ScanTelemetryTest {

    static final InMemorySpanExporter SPANS = InMemorySpanExporter.create();
    static final AtomicBoolean FAIL = new AtomicBoolean();

    @TestConfiguration
    static class Otel {
        @Bean
        @Primary
        OpenTelemetry testOpenTelemetry() {
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(SPANS)).build())
                    .build();
        }

        @Bean
        ProviderAdapter fakeOpenAi() {
            return new FakeAdapter(Engine.OPENAI, r -> {
                if (FAIL.get()) {
                    throw new ProviderException("fake outage", false, null);
                }
                return FakeAdapter.defaultAnswer(Engine.OPENAI, r);
            });
        }
    }

    @Autowired
    ScanPlanner planner;

    @Autowired
    ScanWorker worker;

    @Autowired
    JdbcClient db;

    record Project(UUID account, UUID id) {}

    @BeforeEach
    void reset() {
        SPANS.reset();
        FAIL.set(false);
    }

    Project projectWithPrompts(int prompts) {
        UUID account = db.sql("INSERT INTO account (name, plan_id) VALUES ('t11', 'starter') RETURNING id")
                .query(UUID.class).single();
        UUID project = db.sql("INSERT INTO project (account_id, domain, country) VALUES (:a, :d, 'CH') RETURNING id")
                .param("a", account).param("d", UUID.randomUUID() + ".ch").query(UUID.class).single();
        db.sql("INSERT INTO project_language (project_id, locale) VALUES (:p, 'de')").param("p", project).update();
        for (int i = 0; i < prompts; i++) {
            UUID id = db.sql("INSERT INTO prompt (account_id, project_id, locale, topic) VALUES (:a, :p, 'de', 'reviews') RETURNING id")
                    .param("a", account).param("p", project).query(UUID.class).single();
            db.sql("INSERT INTO prompt_version (prompt_id, version, account_id, text) VALUES (:id, 1, :a, :t)")
                    .param("id", id).param("a", account).param("t", "Frage " + UUID.randomUUID()).update();
        }
        return new Project(account, project);
    }

    void drain() {
        for (int i = 0; i < 20 && worker.drainOnce(Engine.OPENAI) > 0; i++) {
            // keep draining
        }
    }

    List<SpanData> named(String name) {
        return SPANS.getFinishedSpanItems().stream().filter(s -> s.getName().equals(name)).toList();
    }

    String attr(SpanData s, String key) {
        Object v = s.getAttributes().asMap().entrySet().stream()
                .filter(e -> e.getKey().getKey().equals(key)).map(Map.Entry::getValue).findFirst().orElse(null);
        return v == null ? null : v.toString();
    }

    @Test
    void aScanEmitsPlanUnitCallAndFinalizeSpans() {
        Project p = projectWithPrompts(2);
        ScanPlan plan = planner.start(p.account(), p.id(), MANUAL);
        drain();

        String scanId = plan.scanId().toString();
        SpanData planSpan = named("scan.plan").getFirst();
        assertEquals(scanId, attr(planSpan, "geomaster.scan.id"));
        assertEquals(scanId, attr(planSpan, "langfuse.session.id"), "Langfuse groups a scan as one session");
        assertEquals(String.valueOf(plan.units()), attr(planSpan, "geomaster.scan.units"));

        List<SpanData> units = named("scan.unit");
        assertEquals(plan.units(), units.size(), "one span per unit (2 prompts × 2 baseline samples)");
        assertTrue(units.stream().allMatch(u -> scanId.equals(attr(u, "langfuse.session.id"))));
        Map<String, Long> outcomes = units.stream()
                .collect(Collectors.groupingBy(u -> attr(u, "geomaster.unit.outcome"), Collectors.counting()));
        assertEquals(plan.units(), outcomes.getOrDefault("done", 0L) + outcomes.getOrDefault("cache_hit", 0L));

        List<SpanData> calls = named("chat fake-openai");
        assertEquals(outcomes.getOrDefault("done", 0L), calls.size(), "cache hits make no provider call");
        SpanData call = calls.getFirst();
        assertEquals("chat", attr(call, "gen_ai.operation.name"));
        assertEquals("openai", attr(call, "gen_ai.system"));
        assertEquals("fake-openai", attr(call, "gen_ai.request.model"));
        assertNotNull(attr(call, "gen_ai.usage.input_tokens"));
        assertNotNull(attr(call, "geomaster.cost_chf"), "cost per call (NFR-04)");
        assertEquals("p-openai", attr(call, "geomaster.price_version"));
        assertNull(attr(call, "langfuse.observation.input"), "prompt text is not exported by default");
        assertTrue(units.stream().anyMatch(u -> u.getSpanContext().getSpanId().equals(call.getParentSpanId())),
                "the call is a child of its unit span");

        SpanData fin = named("scan.finalize").getFirst();
        assertEquals(scanId, attr(fin, "geomaster.scan.id"));
        assertEquals("completed", attr(fin, "geomaster.scan.status"));
    }

    @Test
    void failedCallsAndRefusalsAreRecorded() {
        Project p = projectWithPrompts(1);
        FAIL.set(true);
        planner.start(p.account(), p.id(), MANUAL);
        drain();

        SpanData call = named("chat fake-openai").getFirst();
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, call.getStatus().getStatusCode());
        assertEquals("failed", attr(named("scan.finalize").getFirst(), "geomaster.scan.status"));

        SPANS.reset();
        Project empty = projectWithPrompts(0);
        assertThrows(ScanRefusedException.class, () -> planner.start(empty.account(), empty.id(), MANUAL));
        assertEquals("no_prompts", attr(named("scan.plan").getFirst(), "geomaster.scan.refused"));
    }
}
