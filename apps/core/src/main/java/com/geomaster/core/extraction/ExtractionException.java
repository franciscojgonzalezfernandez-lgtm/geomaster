package com.geomaster.core.extraction;

import com.geomaster.core.providers.CostCalculator;
import com.geomaster.core.providers.StructuredResult;

/**
 * An extraction did not produce usable output. {@code reason} is stored as the extraction's
 * {@code status_reason}; retryable ones go back to the queue with backoff (REQ-10).
 */
public final class ExtractionException extends RuntimeException {

    private final String reason;
    private final boolean retryable;
    private final StructuredResult call;
    private final CostCalculator.Cost cost;

    public ExtractionException(String reason, boolean retryable, String message, Throwable cause) {
        this(reason, retryable, message, cause, null, null);
    }

    /** A failure after the model answered: the call was billed, so its usage and cost travel along (REQ-16). */
    public ExtractionException(String reason, boolean retryable, String message, Throwable cause,
                               StructuredResult call, CostCalculator.Cost cost) {
        super(message, cause);
        this.reason = reason;
        this.retryable = retryable;
        this.call = call;
        this.cost = cost;
    }

    /** The billed call, or null when the provider call itself failed. */
    public StructuredResult call() {
        return call;
    }

    public CostCalculator.Cost cost() {
        return cost;
    }

    public String reason() {
        return reason;
    }

    public boolean retryable() {
        return retryable;
    }
}
