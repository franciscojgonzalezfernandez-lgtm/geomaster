package com.geomaster.core.extraction;

/**
 * An extraction did not produce usable output. {@code reason} is stored as the extraction's
 * {@code status_reason}; retryable ones go back to the queue with backoff (REQ-10).
 */
public final class ExtractionException extends RuntimeException {

    private final String reason;
    private final boolean retryable;

    public ExtractionException(String reason, boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.retryable = retryable;
    }

    public String reason() {
        return reason;
    }

    public boolean retryable() {
        return retryable;
    }
}
