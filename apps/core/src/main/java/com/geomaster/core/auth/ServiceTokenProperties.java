package com.geomaster.core.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Service-token verification settings (ADR-0017). Values come from the environment; the web app
 * (Better Auth JWT plugin) is the issuer and publishes its signing keys at {@code jwksUri}.
 *
 * @param jwksUri          web app JWKS endpoint (e.g. {@code https://geo-masterizer.com/api/auth/jwks})
 * @param issuer           expected {@code iss} claim
 * @param audience         expected {@code aud} claim
 * @param maxTokenLifetime upper bound for {@code exp - iat}; tokens are short-lived (≤ 2 min)
 * @param clockSkew        tolerance applied to {@code exp}/{@code iat} checks
 * @param jwksCacheTtl     how long fetched keys are trusted before a background refresh
 * @param jwksMinRefresh   minimum gap between forced refreshes on an unknown {@code kid}
 */
@ConfigurationProperties("geomaster.auth")
public record ServiceTokenProperties(
        URI jwksUri,
        String issuer,
        String audience,
        Duration maxTokenLifetime,
        Duration clockSkew,
        Duration jwksCacheTtl,
        Duration jwksMinRefresh) {

    public ServiceTokenProperties {
        if (jwksUri == null || issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("geomaster.auth.jwks-uri, issuer and audience are required");
        }
        maxTokenLifetime = maxTokenLifetime != null ? maxTokenLifetime : Duration.ofMinutes(2);
        clockSkew = clockSkew != null ? clockSkew : Duration.ofSeconds(30);
        jwksCacheTtl = jwksCacheTtl != null ? jwksCacheTtl : Duration.ofMinutes(10);
        jwksMinRefresh = jwksMinRefresh != null ? jwksMinRefresh : Duration.ofSeconds(30);
    }
}
