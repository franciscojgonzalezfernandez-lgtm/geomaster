package com.geomaster.core.plans;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads {@link Plans} once at startup; a broken {@code plans.json} stops the service from booting. */
@Configuration(proxyBeanMethods = false)
public class PlansConfig {

    @Bean
    Plans plans() {
        return Plans.fromClasspath();
    }
}
