package com.geomaster.core.providers;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Provider configuration (ADR-0007, NFR-12): which model each engine uses, its prices and limits.
 * API keys come from the environment only and are never logged.
 *
 * <pre>
 * geomaster.providers.usd-to-chf=0.80
 * geomaster.providers.engines.openai.enabled=true
 * geomaster.providers.engines.openai.model=...
 * geomaster.providers.engines.openai.api-key=${OPENAI_API_KEY:}
 * geomaster.providers.engines.openai.prices.version=2026-11
 * geomaster.providers.engines.openai.prices.input-per-million-usd=...
 * </pre>
 */
@ConfigurationProperties("geomaster.providers")
public record ProvidersProperties(BigDecimal usdToChf, Map<String, EngineProperties> engines) {

    public ProvidersProperties {
        if (usdToChf == null || usdToChf.signum() <= 0) {
            throw new IllegalArgumentException("geomaster.providers.usd-to-chf must be > 0");
        }
        engines = engines == null ? Map.of() : Map.copyOf(engines);
    }

    public EngineProperties engine(Engine engine) {
        EngineProperties p = engines.get(engine.id());
        if (p == null) {
            throw new IllegalStateException("No configuration for engine " + engine.id());
        }
        return p;
    }

    /**
     * @param enabled        false = the engine is skipped by scans (e.g. no key yet)
     * @param model          model id sent to the provider
     * @param apiKey         from the environment; blank means "not configured"
     * @param maxSearches    hard cap of web searches per call (FR-OPS-03)
     * @param maxConcurrency concurrent calls to this provider
     * @param timeout        per-call timeout
     */
    public record EngineProperties(
            boolean enabled,
            String model,
            String apiKey,
            Integer maxSearches,
            Integer maxConcurrency,
            Duration timeout,
            Prices prices) {

        public EngineProperties {
            maxSearches = maxSearches != null ? maxSearches : 2;
            maxConcurrency = maxConcurrency != null ? maxConcurrency : 4;
            timeout = timeout != null ? timeout : Duration.ofSeconds(90);
            if (enabled && (model == null || model.isBlank() || prices == null)) {
                throw new IllegalArgumentException("an enabled engine needs a model and prices");
            }
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        @Override
        public String toString() { // never print the key
            return "EngineProperties[enabled=" + enabled + ", model=" + model + ", apiKey=" + (hasApiKey() ? "***" : "")
                    + ", maxSearches=" + maxSearches + ", maxConcurrency=" + maxConcurrency + ", timeout=" + timeout
                    + ", prices=" + prices + "]";
        }
    }

    /** USD list prices; {@code version} is recorded with every call (REQ-14). */
    public record Prices(
            String version,
            BigDecimal inputPerMillionUsd,
            BigDecimal outputPerMillionUsd,
            BigDecimal perThousandSearchesUsd) {

        public Prices {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("prices.version is required");
            }
            inputPerMillionUsd = nonNegative(inputPerMillionUsd, "input-per-million-usd");
            outputPerMillionUsd = nonNegative(outputPerMillionUsd, "output-per-million-usd");
            perThousandSearchesUsd = nonNegative(perThousandSearchesUsd, "per-thousand-searches-usd");
        }

        private static BigDecimal nonNegative(BigDecimal v, String name) {
            if (v == null || v.signum() < 0) {
                throw new IllegalArgumentException("prices." + name + " is required and must be >= 0");
            }
            return v;
        }
    }
}
