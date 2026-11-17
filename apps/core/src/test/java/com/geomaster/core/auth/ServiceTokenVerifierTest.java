package com.geomaster.core.auth;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceTokenVerifierTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private final TestTokens tokens = new TestTokens("k1");
    private final ServiceTokenVerifier verifier = new ServiceTokenVerifier(
            TestTokens.props(URI.create("https://web.test/api/auth/jwks")),
            tokens.keys(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void acceptsValidTokenAndExposesIdentity() {
        UUID user = UUID.randomUUID();
        UUID acct = UUID.randomUUID();
        String jwt = tokens.sign("EdDSA", "k1",
                TestTokens.claims(NOW.minusSeconds(10), Duration.ofMinutes(2), user.toString(), acct.toString(), "starter"));

        ServiceTokenVerifier.VerifiedToken t = verifier.verify(jwt);

        assertEquals(user, t.userId());
        assertEquals(acct, t.accountId());
        assertEquals("starter", t.planId());
    }

    @Test
    void rejectsExpiredToken() {
        String jwt = tokens.sign("EdDSA", "k1", TestTokens.claims(NOW.minus(Duration.ofMinutes(5)),
                Duration.ofMinutes(2), UUID.randomUUID().toString(), UUID.randomUUID().toString(), "pro"));
        assertInvalid(jwt);
    }

    @Test
    void rejectsLifetimeAboveMaximum() {
        String jwt = tokens.sign("EdDSA", "k1", TestTokens.claims(NOW, Duration.ofHours(1),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "pro"));
        assertInvalid(jwt);
    }

    @Test
    void rejectsTokenIssuedInTheFuture() {
        String jwt = tokens.sign("EdDSA", "k1", TestTokens.claims(NOW.plus(Duration.ofMinutes(5)),
                Duration.ofMinutes(1), UUID.randomUUID().toString(), UUID.randomUUID().toString(), "pro"));
        assertInvalid(jwt);
    }

    @Test
    void rejectsSignatureFromAnotherKeyWithSameKid() {
        TestTokens attacker = new TestTokens("k1");
        assertInvalid(attacker.valid(NOW));
    }

    @Test
    void rejectsUnknownKid() {
        assertInvalid(new TestTokens("k2").valid(NOW));
    }

    @Test
    void rejectsNonEdDsaAlgorithms() {
        String claims = TestTokens.claims(NOW, Duration.ofMinutes(1), UUID.randomUUID().toString(), UUID.randomUUID().toString(), "pro");
        assertInvalid(tokens.sign("HS256", "k1", claims));
        assertInvalid(TestTokens.b64("{\"alg\":\"none\",\"kid\":\"k1\"}") + "." + TestTokens.b64(claims) + ".");
    }

    @Test
    void rejectsTamperedPayload() {
        String jwt = tokens.valid(NOW);
        String[] parts = jwt.split("\\.");
        String other = TestTokens.b64(TestTokens.claims(NOW, Duration.ofMinutes(1),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "premium"));
        assertInvalid(parts[0] + "." + other + "." + parts[2]);
    }

    @Test
    void rejectsWrongIssuerOrAudience() {
        String base = TestTokens.claims(NOW, Duration.ofMinutes(1), UUID.randomUUID().toString(), UUID.randomUUID().toString(), "pro");
        assertInvalid(tokens.sign("EdDSA", "k1", base.replace("\"iss\":\"https://web.test\"", "\"iss\":\"https://evil.test\"")));
        assertInvalid(tokens.sign("EdDSA", "k1", base.replace("\"aud\":\"https://web.test\"", "\"aud\":\"https://evil.test\"")));
    }

    @Test
    void rejectsMissingOrMalformedIdentityClaims() {
        assertInvalid(tokens.sign("EdDSA", "k1", TestTokens.claims(NOW, Duration.ofMinutes(1), UUID.randomUUID().toString(), "not-a-uuid", "pro")));
        assertInvalid(tokens.sign("EdDSA", "k1", TestTokens.claims(NOW, Duration.ofMinutes(1), null, UUID.randomUUID().toString(), "pro")));
        assertInvalid(tokens.sign("EdDSA", "k1", TestTokens.claims(NOW, Duration.ofMinutes(1), UUID.randomUUID().toString(), UUID.randomUUID().toString(), null)));
    }

    @Test
    void acceptsAccountLessProvisioningToken() {
        UUID user = UUID.randomUUID();
        ServiceTokenVerifier.VerifiedToken t = verifier.verify(tokens.sign("EdDSA", "k1",
                TestTokens.claims(NOW, Duration.ofMinutes(1), user.toString(), null, null)));
        assertEquals(user, t.userId());
        assertEquals(false, t.hasAccount());
    }

    @Test
    void rejectsGarbage() {
        assertInvalid("not-a-jwt");
        assertInvalid("");
    }

    private void assertInvalid(String jwt) {
        assertThrows(InvalidServiceTokenException.class, () -> verifier.verify(jwt));
    }
}
