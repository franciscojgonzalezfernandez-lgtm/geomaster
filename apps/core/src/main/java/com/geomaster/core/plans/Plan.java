package com.geomaster.core.plans;

/**
 * One plan from {@code packages/plans/plans.json} (ADR-0010, FR-BIL-03). A {@code null} limit means
 * "custom / unlimited" (enterprise or no cap), mirroring the TypeScript {@code Plan} type.
 */
public record Plan(
        String id,
        boolean selfServe,
        Integer priceChfMonthly,
        Integer domains,
        Integer prompts,
        int engines,
        int languages,
        String scanFrequency,
        Integer competitorsPerDomain,
        Integer artifactsPerMonth,
        Integer seats,
        Integer historyMonths,
        Integer consultingMinutesPerMonth) {

    /** True when {@code requested} units fit within {@code limit} ({@code null} = unlimited). */
    public static boolean within(Integer limit, int requested) {
        return limit == null || requested <= limit;
    }
}
