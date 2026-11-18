package com.geomaster.core.scan;

import com.geomaster.core.providers.AnswerRequest;
import com.geomaster.core.providers.CostCalculator;
import com.geomaster.core.providers.ProviderAdapter;
import com.geomaster.core.providers.ProviderAnswer;
import com.geomaster.core.providers.ProviderException;
import com.geomaster.core.providers.ProvidersProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs one claimed unit: shared-cache lookup, provider call (outside any DB transaction), cost,
 * storage, and the unit's outcome (done / retry with backoff / failed). Idempotent: results are
 * fenced by the unit's attempt number, and the cache key makes a re-run after a crash a cache hit.
 */
public final class UnitExecutor {

    private static final Logger log = LoggerFactory.getLogger(UnitExecutor.class);

    /** Outcome reported to the poller, which feeds the engine's circuit breaker. */
    public enum Outcome { DONE, CACHE_HIT, RETRY, FAILED, LOST_LEASE }

    private final ScanQueueRepository queue;
    private final LlmCallRepository calls;
    private final CostCalculator costs;
    private final ProvidersProperties providers;
    private final ScanWorkerProperties props;
    private final Clock clock;

    public UnitExecutor(ScanQueueRepository queue, LlmCallRepository calls, CostCalculator costs,
                        ProvidersProperties providers, ScanWorkerProperties props, Clock clock) {
        this.queue = queue;
        this.calls = calls;
        this.costs = costs;
        this.providers = providers;
        this.props = props;
        this.clock = clock;
    }

    public Outcome run(ClaimedUnit u, ProviderAdapter adapter) {
        ProvidersProperties.EngineProperties engine = providers.engine(u.engine());
        String key = CacheKey.of(u.promptText(), u.engine(), engine.model(), u.locale(), u.country(), u.region(),
                LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC));

        Optional<UUID> cached = calls.findByCacheKey(key);
        if (cached.isPresent()) {
            return queue.markDone(u, cached.get(), true, BigDecimal.ZERO) ? Outcome.CACHE_HIT : Outcome.LOST_LEASE;
        }

        ProviderAnswer answer;
        try {
            answer = adapter.answerWithSearch(new AnswerRequest(
                    u.promptText(), u.locale(), u.country(), u.region(), engine.maxSearches()));
        } catch (ProviderException e) {
            return failed(u, e.getMessage(), e.retryable());
        } catch (RuntimeException e) {
            log.warn("Unexpected error from {} on unit {}: {}", u.engine().id(), u.id(), e.toString());
            return failed(u, e.toString(), true);
        }

        CostCalculator.Cost cost = costs.cost(u.engine(), answer);
        UUID callId = calls.insert(key, u.engine(), u.locale(), u.country(), u.region(), u.promptText(),
                answer, cost.chf(), cost.priceVersion());
        return queue.markDone(u, callId, false, cost.chf()) ? Outcome.DONE : Outcome.LOST_LEASE;
    }

    private Outcome failed(ClaimedUnit u, String error, boolean retryable) {
        if (retryable && u.attempt() < props.maxAttempts()) {
            return queue.markRetry(u, error, props.backoff(u.attempt())) ? Outcome.RETRY : Outcome.LOST_LEASE;
        }
        return queue.markFailed(u, error) ? Outcome.FAILED : Outcome.LOST_LEASE;
    }
}
