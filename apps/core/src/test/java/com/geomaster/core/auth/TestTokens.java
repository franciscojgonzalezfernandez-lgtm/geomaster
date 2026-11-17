package com.geomaster.core.auth;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.util.Base64URL;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/** Mints Ed25519 JWS tokens the way the Better Auth JWT plugin does, for tests only. */
public final class TestTokens {

    public static final String ISSUER = "https://web.test";
    public static final String AUDIENCE = "https://web.test";

    final KeyPair keyPair;
    final String kid;

    public TestTokens(String kid) {
        try {
            this.keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        this.kid = kid;
    }

    static ServiceTokenProperties props(URI jwksUri) {
        return new ServiceTokenProperties(jwksUri, ISSUER, AUDIENCE, null, null, null, null);
    }

    /** Raw 32-byte public key: the last 32 bytes of the X.509 encoding. */
    byte[] rawPublicKey() {
        byte[] der = keyPair.getPublic().getEncoded();
        return Arrays.copyOfRange(der, der.length - 32, der.length);
    }

    String jwksJson() {
        OctetKeyPair jwk = new OctetKeyPair.Builder(Curve.Ed25519, Base64URL.encode(rawPublicKey())).keyID(kid).build();
        return new JWKSet(jwk).toString();
    }

    String valid(Instant now) {
        return sign("EdDSA", kid, claims(now, Duration.ofMinutes(2), UUID.randomUUID().toString(), UUID.randomUUID().toString(), "pro"));
    }

    public static String claims(Instant iat, Duration life, String sub, String acct, String plan) {
        StringBuilder sb = new StringBuilder("{\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE + "\"");
        sb.append(",\"iat\":").append(iat.getEpochSecond());
        sb.append(",\"exp\":").append(iat.plus(life).getEpochSecond());
        if (sub != null) sb.append(",\"sub\":\"").append(sub).append('"');
        if (acct != null) sb.append(",\"acct\":\"").append(acct).append('"');
        if (plan != null) sb.append(",\"plan\":\"").append(plan).append('"');
        return sb.append('}').toString();
    }

    public String sign(String alg, String kid, String claimsJson) {
        String header = "{\"alg\":\"" + alg + "\",\"kid\":\"" + kid + "\",\"typ\":\"JWT\"}";
        String input = b64(header) + "." + b64(claimsJson);
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(keyPair.getPrivate());
            sig.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + Base64URL.encode(sig.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String b64(String json) {
        return Base64URL.encode(json.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public SigningKeys keys() {
        Map<String, java.security.PublicKey> m = Map.of(kid, keyPair.getPublic());
        return k -> java.util.Optional.ofNullable(m.get(k));
    }
}
