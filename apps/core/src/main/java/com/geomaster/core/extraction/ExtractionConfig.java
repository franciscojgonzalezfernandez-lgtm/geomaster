package com.geomaster.core.extraction;

import com.geomaster.core.providers.CostCalculator;
import com.geomaster.core.providers.claude.AnthropicClients;
import com.geomaster.core.providers.claude.ClaudeAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The extractor, wired only when {@code geomaster.extractor.enabled=true}; then a key is mandatory (fail fast). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ExtractorProperties.class)
public class ExtractionConfig {

    @Bean
    @ConditionalOnProperty(prefix = "geomaster.extractor", name = "enabled", havingValue = "true")
    Extractor extractor(ExtractorProperties props, CostCalculator costs) {
        if (!props.hasApiKey()) {
            throw new IllegalStateException("geomaster.extractor is enabled but ANTHROPIC_API_KEY is not set");
        }
        ClaudeAdapter claude = new ClaudeAdapter(AnthropicClients.create(props.apiKey(), props.timeout()));
        return new Extractor(claude, props, ExtractorPrompt.v1(), costs);
    }
}
