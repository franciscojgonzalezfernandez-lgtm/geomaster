package com.geomaster.core.scan;

import com.geomaster.core.providers.Engine;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueuePartsTest {

    @Test
    void backoffDoublesAndIsCapped() {
        ScanWorkerProperties p = new ScanWorkerProperties(null, null, null, null, Duration.ofSeconds(30), null, null);
        assertEquals(Duration.ofSeconds(30), p.backoff(1));
        assertEquals(Duration.ofSeconds(60), p.backoff(2));
        assertEquals(Duration.ofSeconds(120), p.backoff(3));
        assertEquals(Duration.ofHours(1), p.backoff(50));
    }

    @Test
    void cacheKeyChangesWithEveryDimensionAndHidesText() {
        LocalDate day = LocalDate.parse("2026-11-18");
        String base = CacheKey.of("Beste Skischule?", Engine.OPENAI, "m", "de", "CH", "SG", day);
        assertFalse(base.contains("Beste"), "only a hash of the prompt text is stored");
        assertEquals(base, CacheKey.of("Beste Skischule?", Engine.OPENAI, "m", "de", "CH", " sg ", day));
        assertNotEquals(base, CacheKey.of("Beste Skischule!", Engine.OPENAI, "m", "de", "CH", "SG", day));
        assertNotEquals(base, CacheKey.of("Beste Skischule?", Engine.GEMINI, "m", "de", "CH", "SG", day));
        assertNotEquals(base, CacheKey.of("Beste Skischule?", Engine.OPENAI, "m2", "de", "CH", "SG", day));
        assertNotEquals(base, CacheKey.of("Beste Skischule?", Engine.OPENAI, "m", "en", "CH", "SG", day));
        assertNotEquals(base, CacheKey.of("Beste Skischule?", Engine.OPENAI, "m", "de", "AT", "SG", day));
        assertNotEquals(base, CacheKey.of("Beste Skischule?", Engine.OPENAI, "m", "de", "CH", "SG", day.plusDays(1)));
    }

    @Test
    void breakerOpensAfterThresholdAndClosesAfterCooldown() {
        MutableClock clock = new MutableClock();
        CircuitBreaker b = new CircuitBreaker(3, Duration.ofMinutes(5), clock);
        b.failure();
        b.failure();
        b.success(); // resets
        b.failure();
        b.failure();
        assertTrue(b.allows());
        b.failure();
        assertFalse(b.allows());
        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertTrue(b.allows());
    }

    @Test
    void citationDomain() {
        assertEquals("rideflumserberg.ch", LlmCallRepository.domainOf("https://www.RideFlumserberg.ch/kurse?x=1"));
        assertEquals("", LlmCallRepository.domainOf("not a url"));
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-18T10:00:00Z");

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
