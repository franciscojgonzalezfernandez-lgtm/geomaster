package com.geomaster.core.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Verifies the web→core service token (ADR-0017, REQ-4/REQ-5): an EdDSA (Ed25519) JWS signed by
 * the web app's Better Auth JWT plugin, carrying {@code sub} (user id), {@code acct} (account id)
 * and {@code plan}. Signature is checked with the JDK's native Ed25519 provider against the key
 * named by {@code kid}; then {@code iss}, {@code aud}, {@code exp}, {@code iat} and a maximum
 * lifetime are enforced. Any failure throws {@link InvalidServiceTokenException}.
 */
public final class ServiceTokenVerifier {

    /** The verified identity carried by a service token. */
    public record VerifiedToken(UUID userId, UUID accountId, String planId) {}

    private final ServiceTokenProperties props;
    private final SigningKeys keys;
    private final Clock clock;

    public ServiceTokenVerifier(ServiceTokenProperties props, SigningKeys keys, Clock clock) {
        this.props = props;
        this.keys = keys;
        this.clock = clock;
    }

    public VerifiedToken verify(String token) {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(token);
        } catch (Exception e) {
            throw new InvalidServiceTokenException("malformed token");
        }

        JWSHeader header = jwt.getHeader();
        // Pin the algorithm: never let the token choose (no "none", no HS* with a public key).
        if (!JWSAlgorithm.EdDSA.equals(header.getAlgorithm()) && !JWSAlgorithm.Ed25519.equals(header.getAlgorithm())) {
            throw new InvalidServiceTokenException("unsupported alg");
        }
        PublicKey key = keys.find(header.getKeyID())
                .orElseThrow(() -> new InvalidServiceTokenException("unknown signing key"));
        if (!signatureValid(jwt, key)) {
            throw new InvalidServiceTokenException("bad signature");
        }

        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (Exception e) {
            throw new InvalidServiceTokenException("malformed claims");
        }
        checkTimes(claims);
        if (!props.issuer().equals(claims.getIssuer())) {
            throw new InvalidServiceTokenException("wrong issuer");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(props.audience())) {
            throw new InvalidServiceTokenException("wrong audience");
        }
        return new VerifiedToken(
                uuidClaim(claims.getSubject(), "sub"),
                uuidClaim(stringClaim(claims, "acct"), "acct"),
                requireText(stringClaim(claims, "plan"), "plan"));
    }

    private static boolean signatureValid(SignedJWT jwt, PublicKey key) {
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(key);
            sig.update(jwt.getSigningInput());
            return sig.verify(jwt.getSignature().decode());
        } catch (Exception e) {
            return false;
        }
    }

    private void checkTimes(JWTClaimsSet claims) {
        Date exp = claims.getExpirationTime();
        Date iat = claims.getIssueTime();
        if (exp == null || iat == null) {
            throw new InvalidServiceTokenException("exp and iat are required");
        }
        Instant now = clock.instant();
        if (!now.isBefore(exp.toInstant().plus(props.clockSkew()))) {
            throw new InvalidServiceTokenException("expired");
        }
        if (iat.toInstant().isAfter(now.plus(props.clockSkew()))) {
            throw new InvalidServiceTokenException("issued in the future");
        }
        if (exp.toInstant().isAfter(iat.toInstant().plus(props.maxTokenLifetime()))) {
            throw new InvalidServiceTokenException("lifetime exceeds maximum");
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (Exception e) {
            throw new InvalidServiceTokenException(name + " must be a string");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidServiceTokenException("missing " + name);
        }
        return value;
    }

    private static UUID uuidClaim(String value, String name) {
        try {
            return UUID.fromString(requireText(value, name));
        } catch (IllegalArgumentException e) {
            throw new InvalidServiceTokenException(name + " is not a UUID");
        }
    }
}
