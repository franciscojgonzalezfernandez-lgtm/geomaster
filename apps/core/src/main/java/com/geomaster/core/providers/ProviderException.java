package com.geomaster.core.providers;

/**
 * A provider call failed. {@link #retryable()} drives the queue: timeouts, 429 and 5xx back off and
 * retry (FR-SCN-05); anything else fails the unit immediately.
 */
public final class ProviderException extends RuntimeException {

    private final boolean retryable;

    public ProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }

    /** Classify an HTTP status from a provider. */
    public static ProviderException fromStatus(int status, String message, Throwable cause) {
        boolean retry = status == 408 || status == 429 || status >= 500;
        return new ProviderException("HTTP " + status + ": " + message, retry, cause);
    }
}
