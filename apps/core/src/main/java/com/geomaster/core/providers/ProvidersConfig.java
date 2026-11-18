package com.geomaster.core.providers;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provider configuration and cost calculation. Real adapters register themselves in later tasks. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProvidersProperties.class)
public class ProvidersConfig {

    @Bean
    CostCalculator costCalculator(ProvidersProperties props) {
        return new CostCalculator(props);
    }
}
