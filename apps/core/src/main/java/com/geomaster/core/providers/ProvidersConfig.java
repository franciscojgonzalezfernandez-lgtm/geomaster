package com.geomaster.core.providers;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provider configuration, cost calculation and the real engine adapters (enabled by configuration). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProvidersProperties.class)
public class ProvidersConfig {

    @Bean
    CostCalculator costCalculator(ProvidersProperties props) {
        return new CostCalculator(props);
    }

    /**
     * Real provider adapters. Tests that plug {@link FakeAdapter}s set
     * {@code geomaster.providers.real-adapters=false} so no real client is created.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "geomaster.providers", name = "real-adapters", havingValue = "true", matchIfMissing = true)
    static class RealAdapters {

        /** ChatGPT engine; registered only when enabled, and then a key is mandatory (fail fast). */
        @Bean
        @ConditionalOnProperty(prefix = "geomaster.providers.engines.openai", name = "enabled", havingValue = "true")
        OpenAiAdapter openAiAdapter(ProvidersProperties props) {
            ProvidersProperties.EngineProperties p = props.engine(Engine.OPENAI);
            if (!p.hasApiKey()) {
                throw new IllegalStateException("geomaster.providers.engines.openai is enabled but OPENAI_API_KEY is not set");
            }
            OpenAIClient client = OpenAIOkHttpClient.builder()
                    .apiKey(p.apiKey())
                    .timeout(p.timeout())
                    .maxRetries(0) // the scan queue owns retries (REQ-10)
                    .build();
            return new OpenAiAdapter(client, p.model());
        }
    }
}
