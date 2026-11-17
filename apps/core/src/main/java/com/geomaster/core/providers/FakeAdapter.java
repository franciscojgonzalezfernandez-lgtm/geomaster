package com.geomaster.core.providers;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Deterministic stand-in for a real engine (tests and local dev without API keys). By default it
 * answers with a fixed text that echoes the prompt and cites one URL; tests can plug any behaviour,
 * including throwing {@link ProviderException} to simulate outages.
 */
public final class FakeAdapter implements ProviderAdapter {

    private final Engine engine;
    private final Function<AnswerRequest, ProviderAnswer> behaviour;

    public FakeAdapter(Engine engine) {
        this(engine, req -> defaultAnswer(engine, req));
    }

    public FakeAdapter(Engine engine, Function<AnswerRequest, ProviderAnswer> behaviour) {
        this.engine = engine;
        this.behaviour = behaviour;
    }

    @Override
    public Engine engine() {
        return engine;
    }

    @Override
    public ProviderAnswer answerWithSearch(AnswerRequest request) {
        return behaviour.apply(request);
    }

    public static ProviderAnswer defaultAnswer(Engine engine, AnswerRequest req) {
        int searches = Math.min(1, req.maxSearches());
        return new ProviderAnswer(
                "[" + engine.id() + "/" + req.locale() + "] fake answer to: " + req.promptText(),
                List.of(new ProviderAnswer.Citation("https://example.com/" + engine.id(), "Example")),
                searches == 0 ? List.of() : List.of(req.promptText()),
                searches,
                new ProviderAnswer.Usage(800, 200),
                "fake-" + engine.id(),
                "fake-" + engine.id() + "-1",
                engine == Engine.GEMINI ? "<div>fake search suggestions</div>" : null,
                Duration.ofMillis(5));
    }
}
