package com.geomaster.core.auth;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Ed25519 signing keys fetched from the web app's JWKS endpoint and cached (ADR-0017). Keys are
 * fetched lazily, refreshed after {@code jwksCacheTtl}, and re-fetched when an unknown {@code kid}
 * shows up (key rotation) — but at most once per {@code jwksMinRefresh}, so garbage tokens cannot
 * turn core into a JWKS request amplifier. A failed refresh keeps the last good keys.
 */
public final class RemoteJwks implements SigningKeys {

    private static final Logger log = LoggerFactory.getLogger(RemoteJwks.class);
    private static final long MAX_JWKS_BYTES = 64 * 1024;

    private final ServiceTokenProperties props;
    private final HttpClient http;
    private final Clock clock;

    private Map<String, PublicKey> keys = Map.of();
    private Instant fetchedAt = Instant.EPOCH;
    private Instant lastAttempt = Instant.EPOCH;

    public RemoteJwks(ServiceTokenProperties props, HttpClient http, Clock clock) {
        this.props = props;
        this.http = http;
        this.clock = clock;
    }

    @Override
    public synchronized Optional<PublicKey> find(String kid) {
        if (kid == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        boolean stale = now.isAfter(fetchedAt.plus(props.jwksCacheTtl()));
        boolean unknown = !keys.containsKey(kid);
        if ((stale || unknown) && !now.isBefore(lastAttempt.plus(props.jwksMinRefresh()))) {
            refresh(now);
        }
        return Optional.ofNullable(keys.get(kid));
    }

    private void refresh(Instant now) {
        lastAttempt = now;
        try {
            HttpRequest req = HttpRequest.newBuilder(props.jwksUri())
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("JWKS fetch from {} returned HTTP {}", props.jwksUri(), res.statusCode());
                return;
            }
            if (res.body().length() > MAX_JWKS_BYTES) {
                log.warn("JWKS from {} exceeds {} bytes — ignored", props.jwksUri(), MAX_JWKS_BYTES);
                return;
            }
            Map<String, PublicKey> fresh = new HashMap<>();
            for (JWK jwk : JWKSet.parse(res.body()).getKeys()) {
                if (jwk instanceof OctetKeyPair okp && Curve.Ed25519.equals(okp.getCurve()) && okp.getKeyID() != null) {
                    fresh.put(okp.getKeyID(), Ed25519Keys.fromRaw(okp.getDecodedX()));
                }
            }
            keys = Map.copyOf(fresh);
            fetchedAt = now;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("JWKS fetch interrupted");
        } catch (Exception e) {
            log.warn("JWKS fetch from {} failed: {}", props.jwksUri(), e.toString());
        }
    }
}
