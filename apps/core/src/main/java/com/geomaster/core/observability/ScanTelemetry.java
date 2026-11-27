package com.geomaster.core.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Spans per scan and per LLM call (NFR-10, ADR-0012). In production the OpenTelemetry Java agent
 * supplies the SDK and exports to Langfuse over OTLP; without the agent every call is a no-op.
 *
 * <p>Attribute names follow the OpenTelemetry GenAI conventions ({@code gen_ai.*}) where they exist,
 * plus {@code langfuse.session.id} = scan id so Langfuse groups a scan's calls into one session.
 * Prompt and answer text are attached only when {@code captureContent} is on (off by default).
 */
public final class ScanTelemetry {

    public static final String INSTRUMENTATION = "com.geomaster.core.scan";

    static final AttributeKey<String> SCAN_ID = AttributeKey.stringKey("geomaster.scan.id");
    static final AttributeKey<String> ACCOUNT_ID = AttributeKey.stringKey("geomaster.account.id");
    static final AttributeKey<String> PROJECT_ID = AttributeKey.stringKey("geomaster.project.id");
    static final AttributeKey<String> UNIT_ID = AttributeKey.stringKey("geomaster.unit.id");
    static final AttributeKey<String> TRIGGER = AttributeKey.stringKey("geomaster.scan.trigger");
    static final AttributeKey<Long> UNITS = AttributeKey.longKey("geomaster.scan.units");
    static final AttributeKey<Long> SAMPLES = AttributeKey.longKey("geomaster.scan.samples");
    static final AttributeKey<String> SCAN_STATUS = AttributeKey.stringKey("geomaster.scan.status");
    static final AttributeKey<String> LOCALE = AttributeKey.stringKey("geomaster.locale");
    static final AttributeKey<Long> ATTEMPT = AttributeKey.longKey("geomaster.unit.attempt");
    static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("geomaster.unit.outcome");
    static final AttributeKey<Boolean> CACHE_HIT = AttributeKey.booleanKey("geomaster.cache_hit");
    static final AttributeKey<Long> SEARCHES = AttributeKey.longKey("geomaster.web_searches");
    static final AttributeKey<Double> COST_CHF = AttributeKey.doubleKey("geomaster.cost_chf");
    static final AttributeKey<String> PRICE_VERSION = AttributeKey.stringKey("geomaster.price_version");
    static final AttributeKey<String> SESSION = AttributeKey.stringKey("langfuse.session.id");
    static final AttributeKey<String> GEN_AI_OPERATION = AttributeKey.stringKey("gen_ai.operation.name");
    static final AttributeKey<String> GEN_AI_SYSTEM = AttributeKey.stringKey("gen_ai.system");
    static final AttributeKey<String> GEN_AI_REQUEST_MODEL = AttributeKey.stringKey("gen_ai.request.model");
    static final AttributeKey<String> GEN_AI_RESPONSE_MODEL = AttributeKey.stringKey("gen_ai.response.model");
    static final AttributeKey<Long> GEN_AI_INPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.input_tokens");
    static final AttributeKey<Long> GEN_AI_OUTPUT_TOKENS = AttributeKey.longKey("gen_ai.usage.output_tokens");
    static final AttributeKey<String> EXTRACTION_ID = AttributeKey.stringKey("geomaster.extraction.id");
    static final AttributeKey<String> EXTRACTOR_VERSION = AttributeKey.stringKey("geomaster.extractor.version");
    static final AttributeKey<Long> GROUNDING_REJECTIONS = AttributeKey.longKey("geomaster.extraction.grounding_rejections");
    static final AttributeKey<Long> BRANDS = AttributeKey.longKey("geomaster.extraction.brands");
    static final AttributeKey<String> EXTRACTION_REASON = AttributeKey.stringKey("geomaster.extraction.reason");
    static final AttributeKey<String> INPUT = AttributeKey.stringKey("langfuse.observation.input");
    static final AttributeKey<String> OUTPUT = AttributeKey.stringKey("langfuse.observation.output");

    private final Tracer tracer;
    private final boolean captureContent;

    public ScanTelemetry(OpenTelemetry otel, boolean captureContent) {
        this.tracer = otel.getTracer(INSTRUMENTATION);
        this.captureContent = captureContent;
    }

    /** A short span around planning a scan; the plan's figures are set by the caller on success. */
    public Span startPlan(UUID accountId, UUID projectId, String trigger) {
        return tracer.spanBuilder("scan.plan").setSpanKind(SpanKind.INTERNAL)
                .setAttribute(ACCOUNT_ID, accountId.toString())
                .setAttribute(PROJECT_ID, projectId.toString())
                .setAttribute(TRIGGER, trigger)
                .startSpan();
    }

    public void planned(Span span, UUID scanId, int units, int samples) {
        span.setAttribute(SCAN_ID, scanId.toString());
        span.setAttribute(SESSION, scanId.toString());
        span.setAttribute(UNITS, units);
        span.setAttribute(SAMPLES, samples);
    }

    /** Marks the scan closed (REQ-15) with its final status. */
    public void finished(UUID scanId, UUID accountId, String status) {
        Span span = tracer.spanBuilder("scan.finalize").setSpanKind(SpanKind.INTERNAL)
                .setAttribute(SCAN_ID, scanId.toString())
                .setAttribute(SESSION, scanId.toString())
                .setAttribute(ACCOUNT_ID, accountId.toString())
                .setAttribute(SCAN_STATUS, status)
                .startSpan();
        span.end();
    }

    /** Runs one queue unit inside a {@code scan.unit} span; the outcome's name is recorded. */
    public <T extends Enum<T>> T unit(UUID scanId, UUID accountId, UUID unitId, String engine, String locale,
                                      int attempt, Supplier<T> body) {
        Span span = tracer.spanBuilder("scan.unit").setSpanKind(SpanKind.INTERNAL)
                .setAttribute(SCAN_ID, scanId.toString())
                .setAttribute(SESSION, scanId.toString())
                .setAttribute(ACCOUNT_ID, accountId.toString())
                .setAttribute(UNIT_ID, unitId.toString())
                .setAttribute(GEN_AI_SYSTEM, engine)
                .setAttribute(LOCALE, locale)
                .setAttribute(ATTEMPT, (long) attempt)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            T outcome = body.get();
            span.setAttribute(OUTCOME, outcome.name().toLowerCase());
            return outcome;
        } catch (RuntimeException e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }

    /** Records an answer served from the shared cache (no provider call, no cost). */
    public void cacheHit() {
        Span.current().setAttribute(CACHE_HIT, true);
    }

    /** Starts the provider call span ({@code chat <model>}), a child of the current unit span. */
    public Span startCall(String engine, String model, String prompt) {
        Span span = tracer.spanBuilder("chat " + model).setSpanKind(SpanKind.CLIENT)
                .setAttribute(GEN_AI_OPERATION, "chat")
                .setAttribute(GEN_AI_SYSTEM, engine)
                .setAttribute(GEN_AI_REQUEST_MODEL, model)
                .startSpan();
        if (captureContent) {
            span.setAttribute(INPUT, prompt);
        }
        return span;
    }

    /** Usage, searches and cost of a finished call (NFR-04: the same figures stored in Postgres). */
    public void callSucceeded(Span span, String responseModel, long inputTokens, long outputTokens, int searches,
                              BigDecimal costChf, String priceVersion, String answer) {
        if (responseModel != null) {
            span.setAttribute(GEN_AI_RESPONSE_MODEL, responseModel);
        }
        span.setAttribute(GEN_AI_INPUT_TOKENS, inputTokens);
        span.setAttribute(GEN_AI_OUTPUT_TOKENS, outputTokens);
        span.setAttribute(SEARCHES, (long) searches);
        span.setAttribute(COST_CHF, costChf.doubleValue());
        span.setAttribute(PRICE_VERSION, priceVersion);
        if (captureContent && answer != null) {
            span.setAttribute(OUTPUT, answer);
        }
        span.end();
    }

    public void callFailed(Span span, Throwable error) {
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, error.getClass().getSimpleName());
        span.end();
    }

    /**
     * Starts the extractor call span ({@code chat <model>}, extraction-analysis REQ-19). The session is
     * the latest scan that used the answer; the answer text is attached only with content capture on.
     */
    public Span startExtraction(UUID extractionId, UUID sessionScanId, String model, String extractorVersion,
                                int attempt, String answer) {
        var b = tracer.spanBuilder("chat " + model).setSpanKind(SpanKind.CLIENT)
                .setAttribute(GEN_AI_OPERATION, "chat")
                .setAttribute(GEN_AI_SYSTEM, "claude")
                .setAttribute(GEN_AI_REQUEST_MODEL, model)
                .setAttribute(EXTRACTION_ID, extractionId.toString())
                .setAttribute(EXTRACTOR_VERSION, extractorVersion)
                .setAttribute(ATTEMPT, (long) attempt);
        if (sessionScanId != null) {
            b.setAttribute(SCAN_ID, sessionScanId.toString()).setAttribute(SESSION, sessionScanId.toString());
        }
        Span span = b.startSpan();
        if (captureContent && answer != null) {
            span.setAttribute(INPUT, answer);
        }
        return span;
    }

    /** Usage, cost and grounding figures of an extraction call; ends the span. */
    public void extractionUsage(Span span, String responseModel, long inputTokens, long outputTokens,
                                BigDecimal costChf, String priceVersion) {
        if (responseModel != null) {
            span.setAttribute(GEN_AI_RESPONSE_MODEL, responseModel);
        }
        span.setAttribute(GEN_AI_INPUT_TOKENS, inputTokens);
        span.setAttribute(GEN_AI_OUTPUT_TOKENS, outputTokens);
        if (costChf != null) {
            span.setAttribute(COST_CHF, costChf.doubleValue());
            span.setAttribute(PRICE_VERSION, priceVersion);
        }
    }

    public void extractionDone(Span span, int brands, int groundingRejections, String outputJson) {
        span.setAttribute(BRANDS, (long) brands);
        span.setAttribute(GROUNDING_REJECTIONS, (long) groundingRejections);
        if (captureContent && outputJson != null) {
            span.setAttribute(OUTPUT, outputJson);
        }
        span.end();
    }

    public void extractionFailed(Span span, String reason, Throwable error) {
        span.setAttribute(EXTRACTION_REASON, reason);
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, reason);
        span.end();
    }
}
