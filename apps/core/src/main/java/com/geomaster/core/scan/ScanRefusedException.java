package com.geomaster.core.scan;

/** A scan was not started; {@link #reason()} says why (mapped to HTTP by the scans API, T6). */
public final class ScanRefusedException extends RuntimeException {

    public enum Reason {
        /** REQ-6: a scan for this project is already queued or running. */
        ALREADY_ACTIVE,
        /** REQ-5: the plan's scan frequency does not allow another scan yet. */
        FREQUENCY,
        /** REQ-18: the budget guard refused (its code is in the message). */
        BUDGET,
        /** The project is paused or archived. */
        PROJECT_INACTIVE,
        /** Nothing to ask: no active prompts in the measured languages. */
        NO_PROMPTS,
        /** No engine the plan allows is enabled in configuration. */
        NO_ENGINES
    }

    private final Reason reason;

    public ScanRefusedException(Reason reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
