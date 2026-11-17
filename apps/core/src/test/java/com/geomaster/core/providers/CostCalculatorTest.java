package com.geomaster.core.providers;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CostCalculatorTest {

    private static ProvidersProperties props(ProvidersProperties.Prices prices) {
        return new ProvidersProperties(new BigDecimal("0.80"), Map.of("openai",
                new ProvidersProperties.EngineProperties(true, "m", "k", null, null, null, prices)));
    }

    private static ProviderAnswer answer(long in, long out, int searches) {
        return new ProviderAnswer("t", List.of(), List.of(), searches, new ProviderAnswer.Usage(in, out),
                "m", "m-1", null, Duration.ZERO);
    }

    @Test
    void tokensPlusSearchesConvertedToChf() {
        // Arbitrary test prices, not real ones: 2 USD / 1M in, 8 USD / 1M out, 10 USD / 1k searches.
        var p = new ProvidersProperties.Prices("test-1", new BigDecimal("2"), new BigDecimal("8"), new BigDecimal("10"));
        CostCalculator.Cost c = new CostCalculator(props(p)).cost(Engine.OPENAI, answer(8_000, 1_000, 2));
        // USD = 8000*2/1e6 + 1000*8/1e6 + 2*10/1000 = 0.016 + 0.008 + 0.02 = 0.044 → CHF 0.0352
        assertEquals(new BigDecimal("0.035200"), c.chf());
        assertEquals("test-1", c.priceVersion());
    }

    @Test
    void zeroUsageCostsNothing() {
        var p = new ProvidersProperties.Prices("v", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        assertEquals(new BigDecimal("0.000000"), new CostCalculator(props(p)).cost(Engine.OPENAI, answer(0, 0, 0)).chf());
    }

    @Test
    void enabledEngineNeedsModelAndPrices() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProvidersProperties.EngineProperties(true, "m", "k", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProvidersProperties.Prices("v", new BigDecimal("-1"), BigDecimal.ONE, BigDecimal.ONE));
    }

    @Test
    void apiKeyNeverPrinted() {
        var e = new ProvidersProperties.EngineProperties(false, null, "sk-secret", null, null, null, null);
        assertTrue(e.hasApiKey());
        assertFalse(e.toString().contains("sk-secret"));
    }

    @Test
    void retryableClassification() {
        assertTrue(ProviderException.fromStatus(429, "slow down", null).retryable());
        assertTrue(ProviderException.fromStatus(503, "down", null).retryable());
        assertTrue(ProviderException.fromStatus(408, "timeout", null).retryable());
        assertFalse(ProviderException.fromStatus(400, "bad", null).retryable());
        assertFalse(ProviderException.fromStatus(401, "key", null).retryable());
    }

    @Test
    void fakeAdapterRespectsSearchCapAndAddsGeminiSuggestions() {
        ProviderAnswer none = new FakeAdapter(Engine.OPENAI).answerWithSearch(new AnswerRequest("q", "de", "CH", null, 0));
        assertEquals(0, none.searchCount());
        ProviderAnswer g = new FakeAdapter(Engine.GEMINI).answerWithSearch(new AnswerRequest("q", "de", "CH", null, 2));
        assertTrue(g.searchSuggestionsHtml() != null);
    }
}
