package com.geomaster.core.scan;

import com.geomaster.core.providers.Engine;
import com.geomaster.core.providers.ProviderAdapter;
import com.geomaster.core.providers.ProvidersProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Background consumers of the scan queue (ADR-0004): one poller per enabled engine, each running
 * units on virtual threads bounded by the engine's {@code max-concurrency}, and skipping the engine
 * while its circuit breaker is open. Engines without an adapter or disabled in config are ignored.
 */
public final class ScanWorker implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ScanWorker.class);

    private final ScanQueueRepository queue;
    private final UnitExecutor executor;
    private final ScanWorkerProperties props;
    private final Map<Engine, ProviderAdapter> adapters = new EnumMap<>(Engine.class);
    private final Map<Engine, Semaphore> permits = new EnumMap<>(Engine.class);
    private final Map<Engine, CircuitBreaker> breakers = new EnumMap<>(Engine.class);
    private final ExecutorService runners = Executors.newVirtualThreadPerTaskExecutor();
    private ScheduledExecutorService pollers;
    private volatile boolean running;

    public ScanWorker(ScanQueueRepository queue, UnitExecutor executor, ScanWorkerProperties props,
                      ProvidersProperties providers, List<ProviderAdapter> available, Clock clock) {
        this.queue = queue;
        this.executor = executor;
        this.props = props;
        for (ProviderAdapter a : available) {
            ProvidersProperties.EngineProperties cfg = providers.engines().get(a.engine().id());
            if (cfg != null && cfg.enabled()) {
                adapters.put(a.engine(), a);
                permits.put(a.engine(), new Semaphore(cfg.maxConcurrency()));
                breakers.put(a.engine(), new CircuitBreaker(props.breakerThreshold(), props.breakerCooldown(), clock));
            }
        }
    }

    /** Engines this worker serves (enabled in config and with an adapter). */
    public List<Engine> engines() {
        return List.copyOf(adapters.keySet());
    }

    /**
     * Claim and run as many units of {@code engine} as there are free permits, waiting for them to
     * finish. The background poller calls it repeatedly; tests call it directly.
     *
     * @return how many units were run
     */
    public int drainOnce(Engine engine) {
        ProviderAdapter adapter = adapters.get(engine);
        if (adapter == null || !breakers.get(engine).allows()) {
            return 0;
        }
        Semaphore sem = permits.get(engine);
        int free = sem.drainPermits();
        if (free == 0) {
            return 0;
        }
        List<ClaimedUnit> units;
        try {
            units = queue.claim(engine, free, props.lease());
        } catch (RuntimeException e) {
            sem.release(free);
            throw e;
        }
        sem.release(free - units.size());
        List<Future<?>> running = new ArrayList<>();
        for (ClaimedUnit u : units) {
            running.add(runners.submit(() -> {
                try {
                    UnitExecutor.Outcome o = executor.run(u, adapter);
                    CircuitBreaker b = breakers.get(engine);
                    if (o == UnitExecutor.Outcome.RETRY || o == UnitExecutor.Outcome.FAILED) {
                        b.failure();
                    } else if (o != UnitExecutor.Outcome.LOST_LEASE) {
                        b.success();
                    }
                } catch (RuntimeException e) {
                    log.warn("Unit {} crashed; its lease will expire and it will be retried: {}", u.id(), e.toString());
                } finally {
                    sem.release();
                }
            }));
        }
        for (Future<?> f : running) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
                // logged inside the task
            }
        }
        return units.size();
    }

    @Override
    public void start() {
        if (!props.enabled() || adapters.isEmpty()) {
            log.info("Scan worker idle (enabled={}, engines={})", props.enabled(), adapters.keySet());
            return;
        }
        pollers = Executors.newScheduledThreadPool(adapters.size(), Thread.ofVirtual().factory());
        long ms = props.pollInterval().toMillis();
        for (Engine e : adapters.keySet()) {
            pollers.scheduleWithFixedDelay(() -> {
                try {
                    drainOnce(e);
                } catch (RuntimeException ex) {
                    log.warn("Poller for {} failed: {}", e.id(), ex.toString());
                }
            }, ms, ms, TimeUnit.MILLISECONDS);
        }
        running = true;
        log.info("Scan worker started for {}", adapters.keySet());
    }

    @Override
    public void stop() {
        running = false;
        if (pollers != null) {
            pollers.shutdownNow();
        }
        runners.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
