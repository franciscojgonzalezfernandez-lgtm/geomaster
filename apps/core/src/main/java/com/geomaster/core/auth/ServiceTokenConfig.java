package com.geomaster.core.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/** Wires service-token authentication onto {@code /v1/*} (actuator and API docs stay open). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServiceTokenProperties.class)
public class ServiceTokenConfig {

    @Bean
    SigningKeys signingKeys(ServiceTokenProperties props) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new RemoteJwks(props, http, Clock.systemUTC());
    }

    @Bean
    ServiceTokenVerifier serviceTokenVerifier(ServiceTokenProperties props, SigningKeys keys) {
        return new ServiceTokenVerifier(props, keys, Clock.systemUTC());
    }

    @Bean
    FilterRegistrationBean<ServiceTokenFilter> serviceTokenFilter(ServiceTokenVerifier verifier) {
        FilterRegistrationBean<ServiceTokenFilter> reg = new FilterRegistrationBean<>(new ServiceTokenFilter(verifier));
        reg.addUrlPatterns("/v1/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }
}
