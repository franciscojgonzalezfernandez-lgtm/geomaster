package com.geomaster.core.extraction;

import com.geomaster.core.providers.ProvidersProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Set;

/**
 * The extractor model and its parameters (extraction-analysis REQ-5, REQ-17, NFR-12). Changing the
 * model, effort, thinking or max tokens changes the fingerprint, so {@code version} must be bumped
 * ({@link ExtractorVersion}). The key is the shared {@code ANTHROPIC_API_KEY}.
 *
 * <pre>
 * geomaster.extractor.enabled=true
 * geomaster.extractor.version=ext-1
 * geomaster.extractor.model=claude-haiku-5-5
 * geomaster.extractor.prices.version=anthropic-2026-11
 * </pre>
 */
@ConfigurationProperties("geomaster.extractor")
public record ExtractorProperties(
        boolean enabled,
        String version,
        String model,
        String apiKey,
        String effort,
        String thinking,
        Integer maxTokens,
        Duration timeout,
        Integer concurrency,
        ProvidersProperties.Prices prices) {

    static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");
    static final Set<String> THINKING = Set.of("disabled", "adaptive");

    public ExtractorProperties {
        version = version == null ? "ext-1" : version;
        model = model == null ? "claude-haiku-5-5" : model;
        effort = effort == null ? "low" : effort;
        thinking = thinking == null ? "disabled" : thinking;
        maxTokens = maxTokens == null ? 2000 : maxTokens;
        timeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        concurrency = concurrency == null ? 4 : concurrency;
        if (!version.matches("ext-[0-9]+")) {
            throw new IllegalArgumentException("geomaster.extractor.version must look like ext-<n>");
        }
        if (!EFFORTS.contains(effort) || !THINKING.contains(thinking) || maxTokens <= 0 || concurrency <= 0) {
            throw new IllegalArgumentException("geomaster.extractor effort/thinking/max-tokens out of range");
        }
        if (enabled && prices == null) {
            throw new IllegalArgumentException("an enabled extractor needs prices");
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() { // never print the key
        return "ExtractorProperties[enabled=" + enabled + ", version=" + version + ", model=" + model
                + ", apiKey=" + (hasApiKey() ? "***" : "") + ", effort=" + effort + ", thinking=" + thinking
                + ", maxTokens=" + maxTokens + ", timeout=" + timeout + ", concurrency=" + concurrency + ", prices=" + prices + "]";
    }
}
