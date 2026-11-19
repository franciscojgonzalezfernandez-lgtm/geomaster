package com.geomaster.core.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Contract tests for the OpenAI adapter against recorded/handwritten Responses API payloads. No network. */
class OpenAiAdapterTest {

    private static Response fixture(String name) throws IOException {
        try (InputStream in = OpenAiAdapterTest.class.getResourceAsStream("/providers/openai/" + name)) {
            return ObjectMappers.jsonMapper().readValue(in, Response.class);
        }
    }

    @Test
    void mapsTextCitationsSearchesUsageAndModelVersion() throws IOException {
        ProviderAnswer a = OpenAiAdapter.toAnswer(fixture("web-search-answer.json"), "chat-latest", Duration.ofMillis(1200));

        assertThat(a.text()).startsWith("Am Flumserberg ist Ride Flumserberg");
        assertThat(a.citations()).extracting(ProviderAnswer.Citation::url)
                .containsExactly("https://www.rideflumserberg.ch/", "https://www.skischule-flumserberg.ch/");
        assertThat(a.citations().getFirst().title()).isEqualTo("Ride Flumserberg");
        assertThat(a.searchQueries()).containsExactly("Snowboardschule Flumserberg", "Flumserberg snowboard lessons");
        assertThat(a.searchCount()).isEqualTo(1); // provider-reported billing counter wins
        assertThat(a.usage()).isEqualTo(new ProviderAnswer.Usage(7421, 312));
        assertThat(a.model()).isEqualTo("chat-latest");
        assertThat(a.modelVersion()).isEqualTo("chat-latest-2026-11-01");
        assertThat(a.searchSuggestionsHtml()).isNull();
        assertThat(a.latency()).isEqualTo(Duration.ofMillis(1200));
    }

    @Test
    void countsSearchActionsWhenProviderOmitsToolUsage() throws IOException {
        String json = new String(OpenAiAdapterTest.class
                .getResourceAsStream("/providers/openai/web-search-answer.json").readAllBytes())
                .replaceAll("\"tool_usage\": \\{.*\\},\n", "");
        assertThat(json).doesNotContain("tool_usage");
        Response r = ObjectMappers.jsonMapper().readValue(json, Response.class);
        assertThat(OpenAiAdapter.toAnswer(r, "chat-latest", Duration.ZERO).searchCount()).isEqualTo(1);
    }

    @Test
    void failedResponseIsPermanent() throws IOException {
        String json = new String(OpenAiAdapterTest.class
                .getResourceAsStream("/providers/openai/web-search-answer.json").readAllBytes())
                .replace("\"status\": \"completed\",\n  \"error\": null",
                        "\"status\": \"failed\",\n  \"error\": {\"code\": \"server_error\", \"message\": \"x\"}");
        Response r = ObjectMappers.jsonMapper().readValue(json, Response.class);
        assertThatThrownBy(() -> OpenAiAdapter.toAnswer(r, "chat-latest", Duration.ZERO))
                .isInstanceOf(ProviderException.class)
                .satisfies(e -> assertThat(((ProviderException) e).retryable()).isFalse());
    }

    @Test
    void emptyOutputIsPermanent() throws IOException {
        Response r = fixture("web-search-answer.json").toBuilder().output(java.util.List.of()).build();
        assertThatThrownBy(() -> OpenAiAdapter.toAnswer(r, "chat-latest", Duration.ZERO))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("no answer");
    }

    @Test
    void requestUsesWebSearchLocationToolCapAndNoStorage() {
        ResponseCreateParams p = OpenAiAdapter.params("chat-latest",
                new AnswerRequest("Beste Snowboardschule?", "de", "CH", "St. Gallen", 2));
        String body = ObjectMappers.jsonMapper().valueToTree(p._body()).toString();

        assertThat(body).contains("\"model\":\"chat-latest\"")
                .contains("\"input\":\"Beste Snowboardschule?\"")
                .contains("\"type\":\"web_search\"")
                .contains("\"country\":\"CH\"")
                .contains("\"region\":\"St. Gallen\"")
                .contains("\"max_tool_calls\":2")
                .contains("\"store\":false")
                .doesNotContain("instructions");
    }

    @Test
    void requestWithoutCountryHasNoLocation() {
        ResponseCreateParams p = OpenAiAdapter.params("chat-latest", new AnswerRequest("q", "en", null, null, 1));
        String body = ObjectMappers.jsonMapper().valueToTree(p._body()).toString();
        assertThat(body).doesNotContain("user_location");
    }
}
