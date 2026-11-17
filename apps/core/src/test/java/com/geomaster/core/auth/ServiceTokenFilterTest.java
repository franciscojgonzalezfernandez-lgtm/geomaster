package com.geomaster.core.auth;

import com.geomaster.core.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTokenFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-17T12:00:00Z");

    private final TestTokens tokens = new TestTokens("k1");
    private final ServiceTokenFilter filter = new ServiceTokenFilter(new ServiceTokenVerifier(
            TestTokens.props(URI.create("https://web.test/api/auth/jwks")), tokens.keys(), Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    void validTokenBindsTenantForTheRestOfTheRequest() throws Exception {
        UUID user = UUID.randomUUID();
        UUID acct = UUID.randomUUID();
        String jwt = tokens.sign("EdDSA", "k1", TestTokens.claims(NOW, Duration.ofMinutes(2), user.toString(), acct.toString(), "pro"));
        AtomicReference<TenantContext.Tenant> seen = new AtomicReference<>();

        MockHttpServletResponse res = run("Bearer " + jwt, (req, r) -> seen.set(TenantContext.current()));

        assertEquals(200, res.getStatus());
        assertEquals(new TenantContext.Tenant(user, acct, "pro"), seen.get());
    }

    @Test
    void missingTokenIs401AndHandlerNeverRuns() throws Exception {
        assertRejected(null);
        assertRejected("Basic dXNlcjpwYXNz");
    }

    @Test
    void invalidTokenIs401AndHandlerNeverRuns() throws Exception {
        assertRejected("Bearer garbage");
        assertRejected("Bearer " + new TestTokens("k1").valid(NOW)); // signed by another key
    }

    private void assertRejected(String authorization) throws Exception {
        AtomicBoolean handlerRan = new AtomicBoolean(false);
        MockHttpServletResponse res = run(authorization, (req, r) -> handlerRan.set(true));
        assertEquals(401, res.getStatus());
        assertEquals("Bearer", res.getHeader("WWW-Authenticate"));
        assertTrue(res.getContentType().startsWith("application/problem+json"));
        assertFalse(handlerRan.get(), "no read or write may run without a valid token (REQ-5)");
    }

    private MockHttpServletResponse run(String authorization, FilterChain chain) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/v1/projects");
        if (authorization != null) {
            req.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, chain);
        return res;
    }
}
