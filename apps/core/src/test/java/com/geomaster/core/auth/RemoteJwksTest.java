package com.geomaster.core.auth;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteJwksTest {

    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicInteger hits = new AtomicInteger();
    private final HttpServer server = start();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T12:00:00Z"));
    private final RemoteJwks jwks = new RemoteJwks(
            TestTokens.props(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks")),
            HttpClient.newHttpClient(), clock);

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void fetchesLazilyAndCaches() {
        TestTokens k1 = new TestTokens("k1");
        body.set(k1.jwksJson());

        assertEquals(k1.keyPair.getPublic(), jwks.find("k1").orElseThrow());
        jwks.find("k1");
        assertEquals(1, hits.get(), "cached key must not refetch");
    }

    @Test
    void unknownKidRefetchesButIsRateLimited() {
        body.set(new TestTokens("k1").jwksJson());
        jwks.find("k1");

        assertTrue(jwks.find("nope").isEmpty());
        assertTrue(jwks.find("nope").isEmpty());
        assertEquals(1, hits.get(), "unknown kids within the min-refresh window must not hit the JWKS endpoint");

        TestTokens rotated = new TestTokens("k2");
        body.set(rotated.jwksJson());
        clock.advance(Duration.ofSeconds(31));
        assertEquals(rotated.keyPair.getPublic(), jwks.find("k2").orElseThrow(), "rotation picked up after the window");
        assertEquals(2, hits.get());
    }

    @Test
    void failedRefreshKeepsLastGoodKeys() {
        TestTokens k1 = new TestTokens("k1");
        body.set(k1.jwksJson());
        jwks.find("k1");

        body.set(null); // endpoint now returns 500
        clock.advance(Duration.ofMinutes(11)); // past cache TTL
        assertEquals(k1.keyPair.getPublic(), jwks.find("k1").orElseThrow());
    }

    private HttpServer start() {
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            s.createContext("/jwks", ex -> {
                hits.incrementAndGet();
                String b = body.get();
                byte[] out = (b == null ? "boom" : b).getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(b == null ? 500 : 200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            s.start();
            return s;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
