package com.geomaster.core.providers;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionWebSearch;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.WebSearchTool;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ChatGPT engine over the official OpenAI Java SDK (ADR-0007): Responses API with the {@code web_search}
 * tool, an approximate user location and a hard cap on tool calls (FR-SCN-01, FR-OPS-03).
 *
 * <p>The prompt is sent as-is, with no system instructions, so the answer resembles what a user of the
 * consumer app would get. Responses are not stored at OpenAI ({@code store=false}). The SDK's own
 * retries are disabled: the scan queue owns retries and backoff (REQ-10).
 */
public final class OpenAiAdapter implements ProviderAdapter {

    private static final String INSUFFICIENT_QUOTA = "insufficient_quota";

    private final OpenAIClient client;
    private final String model;

    public OpenAiAdapter(OpenAIClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public Engine engine() {
        return Engine.OPENAI;
    }

    @Override
    public ProviderAnswer answerWithSearch(AnswerRequest request) {
        ResponseCreateParams params = params(model, request);
        long started = System.nanoTime();
        Response response;
        try {
            response = client.responses().create(params);
        } catch (OpenAIServiceException e) {
            // Never propagate the provider's body: it may echo the prompt or request headers.
            String detail = e.type().orElse("error") + e.code().map(c -> "/" + c).orElse("");
            if (e.type().filter(INSUFFICIENT_QUOTA::equals).isPresent()) {
                // A 429 that will not heal by itself: no credits left. Fail fast instead of retrying.
                throw new ProviderException("openai " + detail, false, e);
            }
            throw ProviderException.fromStatus(e.statusCode(), "openai " + detail, e);
        } catch (OpenAIIoException e) {
            throw new ProviderException("openai I/O error: " + e.getClass().getSimpleName(), true, e);
        }
        return toAnswer(response, model, Duration.ofNanos(System.nanoTime() - started));
    }

    static ResponseCreateParams params(String model, AnswerRequest request) {
        WebSearchTool.Builder search = WebSearchTool.builder().type(WebSearchTool.Type.WEB_SEARCH);
        if (request.country() != null) {
            WebSearchTool.UserLocation.Builder location = WebSearchTool.UserLocation.builder()
                    .type(WebSearchTool.UserLocation.Type.APPROXIMATE)
                    .country(request.country());
            if (request.region() != null && !request.region().isBlank()) {
                location.region(request.region());
            }
            search.userLocation(location.build());
        }
        return ResponseCreateParams.builder()
                .model(model)
                .input(request.promptText())
                .addTool(search.build())
                .maxToolCalls((long) request.maxSearches())
                .store(false)
                .build();
    }

    /** Maps a Responses API result; package-private so fixture tests run without network. */
    @SuppressWarnings("deprecation") // Search.query(): read only as a fallback for payloads without `queries`
    static ProviderAnswer toAnswer(Response response, String requestedModel, Duration latency) {
        response.status().ifPresent(status -> {
            if (status.equals(ResponseStatus.FAILED)) {
                String code = response.error().map(err -> err.code().asString()).orElse("unknown");
                throw new ProviderException("openai response failed: " + code, false, null);
            }
        });

        StringBuilder text = new StringBuilder();
        Map<String, ProviderAnswer.Citation> citations = new LinkedHashMap<>();
        List<String> queries = new ArrayList<>();
        int searchCalls = 0;

        for (ResponseOutputItem item : response.output()) {
            if (item.isMessage()) {
                for (var content : item.asMessage().content()) {
                    content.outputText().ifPresent(out -> {
                        if (!text.isEmpty()) {
                            text.append("\n\n");
                        }
                        text.append(out.text());
                        for (ResponseOutputText.Annotation a : out.annotations()) {
                            a.urlCitation().ifPresent(c -> {
                                String url = stripOpenAiTracking(c.url());
                                citations.putIfAbsent(url, new ProviderAnswer.Citation(url, c.title()));
                            });
                        }
                    });
                }
            } else if (item.isWebSearchCall()) {
                ResponseFunctionWebSearch call = item.asWebSearchCall();
                var search = call.action().search();
                if (search.isPresent()) {
                    searchCalls++;
                    search.get().queries().ifPresentOrElse(queries::addAll,
                            () -> search.get().query().ifPresent(queries::add));
                }
            }
        }

        if (text.isEmpty()) {
            String reason = response.incompleteDetails()
                    .flatMap(d -> d.reason().map(r -> r.asString()))
                    .orElse("no text output");
            throw new ProviderException("openai returned no answer: " + reason, false, null);
        }

        // Billing counts search requests; prefer the provider's own counter when it is reported.
        int finalSearchCalls = searchCalls;
        int searchCount = response.toolUsage()
                .map(u -> (int) u.webSearch().numRequests())
                .orElse(finalSearchCalls);

        ProviderAnswer.Usage usage = response.usage()
                .map(u -> new ProviderAnswer.Usage(u.inputTokens(), u.outputTokens()))
                .orElse(new ProviderAnswer.Usage(0, 0));

        return new ProviderAnswer(
                text.toString(),
                List.copyOf(citations.values()),
                queries,
                searchCount,
                usage,
                requestedModel,
                response.model().asString(),
                null,
                latency);
    }

    /** OpenAI appends {@code utm_source=openai} to cited links; drop it so URLs match the source page. */
    static String stripOpenAiTracking(String url) {
        int q = url.indexOf('?');
        if (q < 0) {
            return url;
        }
        int hash = url.indexOf('#', q);
        String query = hash < 0 ? url.substring(q + 1) : url.substring(q + 1, hash);
        String fragment = hash < 0 ? "" : url.substring(hash);
        String kept = java.util.Arrays.stream(query.split("&"))
                .filter(p -> !p.equals("utm_source=openai"))
                .collect(java.util.stream.Collectors.joining("&"));
        return url.substring(0, q) + (kept.isEmpty() ? "" : "?" + kept) + fragment;
    }
}
