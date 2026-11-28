package com.geomaster.core.extraction;

import com.geomaster.core.brands.BrandNormalizer;
import com.geomaster.core.observability.ScanTelemetry;
import com.geomaster.core.scan.CircuitBreaker;
import com.geomaster.core.scan.ScanWorkerProperties;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Consumer of the {@code answer_extraction} queue (extraction-analysis REQ-4, REQ-10, REQ-16, REQ-19):
 * claims with SKIP LOCKED and a lease, calls the extractor outside any transaction, runs the grounding
 * check, and stores the brands with the call's usage and cost. Retryable failures back off up to the
 * scan worker's {@code max-attempts}; refusals and invalid output fail at once. A circuit breaker
 * pauses the extractor after consecutive failures. After each drain the poller runs the
 * {@link ScanAnalyser} sweeps. Idle when no {@link Extractor} is configured.
 */
public final class ExtractionWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ExtractionWorker.class);

    /** Outcome per extraction; drives the breaker. */
    public enum Outcome { DONE, RETRY, FAILED, LOST_LEASE }

    static final int SWEEP_LIMIT = 20;

    private final ExtractionQueueRepository queue;
    private final Extractor extractor;
    private final ScanWorkerProperties props;
    private final ScanTelemetry telemetry;
    private final ScanAnalyser analyser;
    private final Semaphore permits;
    private final CircuitBreaker breaker;
    private final ExecutorService runners = Executors.newVirtualThreadPerTaskExecutor();
    private ScheduledExecutorService poller;
    private volatile boolean running;

    public ExtractionWorker(ExtractionQueueRepository queue, Extractor extractor, int concurrency,
                            ScanWorkerProperties props, ScanTelemetry telemetry, ScanAnalyser analyser, Clock clock) {
        this.queue = queue;
        this.extractor = extractor;
        this.props = props;
        this.telemetry = telemetry;
        this.analyser = analyser;
        this.permits = new Semaphore(concurrency);
        this.breaker = new CircuitBreaker(props.breakerThreshold(), props.breakerCooldown(), clock);
    }

    /**
     * Claim and run as many extractions as there are free permits, waiting for them. The poller calls
     * it repeatedly; tests call it directly.
     *
     * @return how many extractions were run
     */
    public int drainOnce() {
        if (extractor == null || !breaker.allows()) {
            return 0;
        }
        int free = permits.drainPermits();
        if (free == 0) {
            return 0;
        }
        List<ClaimedExtraction> claimed;
        try {
            claimed = queue.claim(extractor.version().version(), free, props.lease());
        } catch (RuntimeException e) {
            permits.release(free);
            throw e;
        }
        permits.release(free - claimed.size());
        List<Future<?>> tasks = new ArrayList<>();
        for (ClaimedExtraction c : claimed) {
            tasks.add(runners.submit(() -> {
                try {
                    Outcome o = run(c);
                    if (o == Outcome.RETRY || o == Outcome.FAILED) {
                        breaker.failure();
                    } else if (o == Outcome.DONE) {
                        breaker.success();
                    }
                } catch (RuntimeException e) {
                    log.warn("Extraction {} crashed; its lease will expire and it will be retried: {}", c.id(), e.toString());
                } finally {
                    permits.release();
                }
            }));
        }
        for (Future<?> f : tasks) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
                // logged inside the task
            }
        }
        return claimed.size();
    }

    /**
     * Analyse finished scans whose extractions are all terminal, then re-resolve scans whose project's
     * brands changed (extraction-analysis REQ-12..REQ-18). The poller calls it after each drain.
     *
     * @return how many scans were analysed or re-resolved
     */
    public int analyseOnce() {
        if (extractor == null) {
            return 0;
        }
        return analyser.analyseReady(extractor.version(), SWEEP_LIMIT) + analyser.reResolveStale(SWEEP_LIMIT);
    }

    Outcome run(ClaimedExtraction c) {
        ExtractorVersion v = extractor.version();
        Span span = telemetry.startExtraction(c.id(), c.scanId(), extractor.model(), v.version(), c.attempt(), c.answerText());
        Extractor.Extraction x;
        try {
            x = extractor.extract(c.answerText());
        } catch (ExtractionException e) {
            if (e.call() != null) {
                telemetry.extractionUsage(span, e.call().model(), e.call().inputTokens(), e.call().outputTokens(),
                        e.cost().chf(), e.cost().priceVersion());
            }
            telemetry.extractionFailed(span, e.reason(), e);
            return failed(c, e.reason(), e.getMessage(), e.retryable(), e);
        } catch (RuntimeException e) {
            telemetry.extractionFailed(span, "unexpected", e);
            log.warn("Unexpected error extracting {}: {}", c.id(), e.toString());
            return failed(c, "unexpected", e.toString(), true, null);
        }
        telemetry.extractionUsage(span, x.call().model(), x.call().inputTokens(), x.call().outputTokens(),
                x.cost().chf(), x.cost().priceVersion());

        GroundingCheck.Result g = GroundingCheck.apply(c.answerText(), x.brands());
        int rejections = g.rejections() + x.dropped();
        List<ExtractionQueueRepository.BrandRow> rows = new ArrayList<>();
        int position = 0;
        for (GroundingCheck.Grounded k : g.kept()) {
            String key = BrandNormalizer.name(k.brand().name());
            if (key.isEmpty()) { // nothing to match on ("!!!"): not a usable brand
                rejections++;
                continue;
            }
            ExtractedBrand b = k.brand();
            rows.add(new ExtractionQueueRepository.BrandRow(b.ordinal(), b.name(), key, b.domain(), b.sentiment(),
                    b.recommended(), b.evidence(), ++position));
        }
        boolean stored = queue.markDone(c, x.call(), x.cost(), rejections, rows);
        telemetry.extractionDone(span, rows.size(), rejections, x.call().json());
        return stored ? Outcome.DONE : Outcome.LOST_LEASE;
    }

    private Outcome failed(ClaimedExtraction c, String reason, String error, boolean retryable, ExtractionException e) {
        var call = e == null ? null : e.call();
        var cost = e == null ? null : e.cost();
        if (retryable && c.attempt() < props.maxAttempts()) {
            return queue.markRetry(c, error, props.backoff(c.attempt()), call, cost) ? Outcome.RETRY : Outcome.LOST_LEASE;
        }
        String terminal = retryable ? "max_attempts" : reason;
        return queue.markFailed(c, terminal, error, call, cost) ? Outcome.FAILED : Outcome.LOST_LEASE;
    }

    @Override
    public void start() {
        if (extractor == null || !props.enabled()) {
            log.info("Extraction worker idle (extractor={}, enabled={})", extractor != null, props.enabled());
            return;
        }
        poller = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        long ms = props.pollInterval().toMillis();
        poller.scheduleWithFixedDelay(() -> {
            try {
                drainOnce();
            } catch (RuntimeException ex) {
                log.warn("Extraction poller failed: {}", ex.toString());
            }
            try {
                analyseOnce();
            } catch (RuntimeException ex) {
                log.warn("Scan analysis failed: {}", ex.toString());
            }
        }, ms, ms, TimeUnit.MILLISECONDS);
        running = true;
        log.info("Extraction worker started ({})", extractor.version().version());
    }

    @Override
    public void stop() {
        running = false;
        if (poller != null) {
            poller.shutdownNow();
        }
        runners.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
