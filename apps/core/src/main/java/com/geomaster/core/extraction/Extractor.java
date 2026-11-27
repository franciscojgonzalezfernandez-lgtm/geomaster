package com.geomaster.core.extraction;

import com.geomaster.core.providers.CostCalculator;
import com.geomaster.core.providers.ProviderAdapter;
import com.geomaster.core.providers.ProviderException;
import com.geomaster.core.providers.StructuredRequest;
import com.geomaster.core.providers.StructuredResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * One extraction call (extraction-analysis REQ-5, REQ-6, REQ-11): the answer goes in as an untrusted
 * document, the model returns schema-constrained JSON, and only output that passes strict validation
 * here is returned. The call knows nothing about projects or accounts, so one extraction serves every
 * account sharing the answer. Grounding and positions run afterwards ({@link GroundingCheck}).
 */
public final class Extractor {

    static final int MAX_NAME = 200;
    static final int MAX_EVIDENCE = 200;
    static final int MAX_DOMAIN = 253;
    static final int MAX_BRANDS = 100;
    private static final Set<String> SENTIMENTS = Set.of("positive", "neutral", "negative");
    private static final Set<String> BRAND_FIELDS = Set.of("name", "domain", "sentiment", "recommended", "evidence");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Validated output plus what to record (REQ-16). {@code dropped} brands had an empty or over-long name. */
    public record Extraction(List<ExtractedBrand> brands, int dropped, StructuredResult call, CostCalculator.Cost cost) {}

    private final ProviderAdapter adapter;
    private final ExtractorProperties props;
    private final ExtractorPrompt prompt;
    private final ExtractorVersion version;
    private final CostCalculator costs;

    public Extractor(ProviderAdapter adapter, ExtractorProperties props, ExtractorPrompt prompt, CostCalculator costs) {
        this.adapter = adapter;
        this.props = props;
        this.prompt = prompt;
        this.version = ExtractorVersion.of(props, prompt);
        this.costs = costs;
    }

    public ExtractorVersion version() {
        return version;
    }

    /** @throws ExtractionException on provider failure, refusal, truncation or invalid output */
    public Extraction extract(String answerText) {
        StructuredResult call;
        try {
            call = adapter.extractStructured(new StructuredRequest(props.model(), prompt.system(), answerText,
                    prompt.instruction(), prompt.schema(), props.maxTokens(), props.effort(), props.thinking()));
        } catch (ProviderException e) {
            throw new ExtractionException(e.retryable() ? "provider_retryable" : "provider_error", e.retryable(),
                    e.getMessage(), e);
        }
        CostCalculator.Cost cost = costs.cost(props.prices(), call.inputTokens(), call.outputTokens());
        switch (call.stopReason()) {
            case StructuredResult.REFUSAL -> throw new ExtractionException("refusal", false, "extractor refused", null);
            case StructuredResult.MAX_TOKENS -> throw new ExtractionException("max_tokens", true, "extractor output cut off", null);
            case StructuredResult.END_TURN -> { }
            default -> throw new ExtractionException("stop_" + call.stopReason(), false,
                    "unexpected stop reason " + call.stopReason(), null);
        }
        List<ExtractedBrand> brands = new ArrayList<>();
        int dropped = parse(call.json(), brands);
        return new Extraction(List.copyOf(brands), dropped, call, cost);
    }

    /** Strict schema v1 validation; adds valid brands to {@code out} and returns how many were dropped. */
    static int parse(String json, List<ExtractedBrand> out) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JacksonException e) {
            throw invalid("not JSON");
        }
        if (root == null || !root.isObject() || root.size() != 1 || !root.has("brands") || !root.get("brands").isArray()) {
            throw invalid("root must be {\"brands\": [...]}");
        }
        JsonNode list = root.get("brands");
        if (list.size() > MAX_BRANDS) {
            throw invalid("more than " + MAX_BRANDS + " brands");
        }
        int dropped = 0;
        int ordinal = 0;
        for (JsonNode b : list) {
            if (!b.isObject()) {
                throw invalid("brand must be an object");
            }
            for (Iterator<String> it = b.propertyNames().iterator(); it.hasNext(); ) {
                String f = it.next();
                if (!BRAND_FIELDS.contains(f)) {
                    throw invalid("unexpected field " + f);
                }
            }
            JsonNode name = b.get("name");
            JsonNode domain = b.get("domain");
            JsonNode sentiment = b.get("sentiment");
            JsonNode recommended = b.get("recommended");
            JsonNode evidence = b.get("evidence");
            if (name == null || !name.isString() || domain == null || !(domain.isNull() || domain.isString())
                    || sentiment == null || !sentiment.isString() || !SENTIMENTS.contains(sentiment.asString())
                    || recommended == null || !recommended.isBoolean() || evidence == null || !evidence.isString()) {
                throw invalid("brand fields do not match schema v1");
            }
            String n = name.asString().strip();
            if (n.isEmpty() || n.length() > MAX_NAME) {
                dropped++;
                continue;
            }
            String d = domain.isNull() ? null : domain.asString().strip();
            if (d != null && (d.isEmpty() || d.length() > MAX_DOMAIN)) {
                d = null;
            }
            String e = evidence.asString().strip();
            if (e.length() > MAX_EVIDENCE) {
                e = e.substring(0, MAX_EVIDENCE).strip(); // a prefix of a verbatim quote stays verbatim
            }
            out.add(new ExtractedBrand(++ordinal, n, d, sentiment.asString(), recommended.asBoolean(), e));
        }
        return dropped;
    }

    private static ExtractionException invalid(String why) {
        return new ExtractionException("invalid_output", false, "extractor output invalid: " + why, null);
    }
}
