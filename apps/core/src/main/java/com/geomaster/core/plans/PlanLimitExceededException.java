package com.geomaster.core.plans;

/**
 * A request would exceed a plan limit (REQ-12, REQ-13). The API maps it to 422 with a message
 * naming the limit; the numbers come from {@code plans.json}, never from code.
 */
public final class PlanLimitExceededException extends RuntimeException {

    private final String planId;
    private final String limit;
    private final int allowed;

    public PlanLimitExceededException(String planId, String limit, int allowed) {
        super("Plan '" + planId + "' allows " + allowed + " " + limit);
        this.planId = planId;
        this.limit = limit;
        this.allowed = allowed;
    }

    public String planId() {
        return planId;
    }

    /** The {@code plans.json} field name, e.g. {@code domains}. */
    public String limit() {
        return limit;
    }

    public int allowed() {
        return allowed;
    }
}
