package com.geomaster.core.providers;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Manual, keyed smoke run: asks one real question to ChatGPT with web search and prints answer,
 * citations and cost. Never runs in CI (needs {@code GEOMASTER_PROVIDER_SMOKE=true} and a key).
 *
 * <pre>
 * GEOMASTER_PROVIDER_SMOKE=true OPENAI_API_KEY=... ./gradlew :core:test --tests '*OpenAiSmokeTest' -i
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "GEOMASTER_PROVIDER_SMOKE", matches = "true")
class OpenAiSmokeTest {

    @Test
    void oneRealQuestion() {
        String model = System.getenv().getOrDefault("OPENAI_MODEL", "chat-latest");
        var adapter = new OpenAiAdapter(OpenAIOkHttpClient.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .timeout(Duration.ofSeconds(90))
                .maxRetries(0)
                .build(), model);

        ProviderAnswer a = adapter.answerWithSearch(new AnswerRequest(
                "Welche Snowboardschule empfiehlst du am Flumserberg?", "de", "CH", "St. Gallen", 2));

        var prices = new ProvidersProperties.Prices("openai-2026-11",
                new BigDecimal("5.00"), new BigDecimal("30.00"), new BigDecimal("10.00"));
        var calc = new CostCalculator(new ProvidersProperties(new BigDecimal("0.80"), Map.of("openai",
                new ProvidersProperties.EngineProperties(true, model, null, 2, 1, Duration.ofSeconds(90), prices))));
        CostCalculator.Cost cost = calc.cost(Engine.OPENAI, a);

        System.out.println("model=" + a.modelVersion() + " latency=" + a.latency().toMillis() + "ms");
        System.out.println("searches=" + a.searchCount() + " queries=" + a.searchQueries());
        System.out.println("tokens in/out=" + a.usage().inputTokens() + "/" + a.usage().outputTokens()
                + " cost=CHF " + cost.chf());
        a.citations().forEach(c -> System.out.println("cite " + c.url()));
        System.out.println(a.text());

        assertThat(a.text()).isNotBlank();
    }
}
