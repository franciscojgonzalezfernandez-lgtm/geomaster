package com.geomaster.core.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry wiring (ADR-0012). The Java agent installs the global SDK and exports over OTLP
 * (Langfuse EU in production, configured with {@code OTEL_*} environment variables); without the agent
 * {@link GlobalOpenTelemetry} is a no-op. Tests provide a {@code @Primary} SDK bean.
 */
@Configuration(proxyBeanMethods = false)
public class TelemetryConfig {

    @Bean
    OpenTelemetry openTelemetry() {
        return GlobalOpenTelemetry.get();
    }

    @Bean
    ScanTelemetry scanTelemetry(OpenTelemetry otel,
                                @Value("${geomaster.telemetry.capture-content:false}") boolean captureContent) {
        return new ScanTelemetry(otel, captureContent);
    }
}
