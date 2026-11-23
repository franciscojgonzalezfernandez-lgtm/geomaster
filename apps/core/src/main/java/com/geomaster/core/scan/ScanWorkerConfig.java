package com.geomaster.core.scan;

import com.geomaster.core.providers.CostCalculator;
import com.geomaster.core.providers.ProviderAdapter;
import com.geomaster.core.providers.ProvidersProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the scan queue consumers (ADR-0004). Adapters are whatever {@link ProviderAdapter} beans exist. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ScanWorkerProperties.class)
public class ScanWorkerConfig {

    @Bean
    UnitExecutor unitExecutor(ScanQueueRepository queue, ScanFinalizer finalizer, LlmCallRepository calls,
                              CostCalculator costs, ProvidersProperties providers, ScanWorkerProperties props) {
        return new UnitExecutor(queue, finalizer, calls, costs, providers, props, Clock.systemUTC());
    }

    @Bean
    ScanWorker scanWorker(ScanQueueRepository queue, UnitExecutor executor, ScanWorkerProperties props,
                          ProvidersProperties providers, ObjectProvider<ProviderAdapter> adapters) {
        // Zero adapters is normal until the real ones are configured: the worker stays idle.
        return new ScanWorker(queue, executor, props, providers, adapters.orderedStream().toList(), Clock.systemUTC());
    }
}
