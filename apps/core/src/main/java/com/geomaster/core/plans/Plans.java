package com.geomaster.core.plans;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Plan limits loaded from {@code packages/plans/plans.json}, the single source of truth
 * (REQ-11, FR-BIL-03, ADR-0010). The build copies that file onto the classpath as
 * {@code plans/plans.json}; core never hard-codes a limit.
 *
 * <p>Loading is strict and fails fast at startup: every plan must list exactly the known fields
 * (a limit is unlimited only when it is an explicit {@code null}, never because a key is missing
 * or misspelled), ids are unique and numbers are non-negative.
 */
public final class Plans {

    public static final String CLASSPATH_LOCATION = "plans/plans.json";

    private static final Set<String> FIELDS = Set.of(
            "id", "selfServe", "priceChfMonthly", "domains", "prompts", "engines", "languages",
            "scanFrequency", "competitorsPerDomain", "artifactsPerMonth", "seats", "historyMonths",
            "consultingMinutesPerMonth");
    private static final Set<String> SCAN_FREQUENCIES = Set.of("once", "weekly");

    private final Map<String, Plan> byId;

    private Plans(Map<String, Plan> byId) {
        this.byId = byId;
    }

    /** Load from the classpath copy of {@code packages/plans/plans.json}. */
    public static Plans fromClasspath() {
        try (InputStream in = Plans.class.getClassLoader().getResourceAsStream(CLASSPATH_LOCATION)) {
            if (in == null) {
                throw new IllegalStateException(CLASSPATH_LOCATION + " not on the classpath (copied from packages/plans by the build)");
            }
            return parse(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + CLASSPATH_LOCATION, e);
        }
    }

    static Plans parse(InputStream json) {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(json);
        JsonNode list = root.path("plans");
        if (!list.isArray() || list.isEmpty()) {
            throw new IllegalStateException("plans.json: 'plans' must be a non-empty array");
        }
        Map<String, Plan> byId = new LinkedHashMap<>();
        for (JsonNode node : list) {
            Set<String> keys = new TreeSet<>(node.propertyNames());
            if (!keys.equals(new TreeSet<>(FIELDS))) {
                Set<String> missing = new TreeSet<>(FIELDS);
                missing.removeAll(keys);
                Set<String> unknown = new TreeSet<>(keys);
                unknown.removeAll(FIELDS);
                throw new IllegalStateException("plans.json: plan " + node.path("id").asString("?")
                        + " missing " + missing + ", unknown " + unknown);
            }
            Plan plan = mapper.treeToValue(node, Plan.class);
            validate(plan);
            if (byId.putIfAbsent(plan.id(), plan) != null) {
                throw new IllegalStateException("plans.json: duplicate plan id " + plan.id());
            }
        }
        return new Plans(Map.copyOf(byId));
    }

    private static void validate(Plan p) {
        if (p.id() == null || p.id().isBlank()) {
            throw new IllegalStateException("plans.json: plan without id");
        }
        if (!SCAN_FREQUENCIES.contains(p.scanFrequency())) {
            throw new IllegalStateException("plans.json: plan " + p.id() + " has unknown scanFrequency " + p.scanFrequency());
        }
        List<Integer> numbers = new ArrayList<>(List.of(p.engines(), p.languages()));
        for (Integer n : new Integer[] {p.priceChfMonthly(), p.domains(), p.prompts(), p.competitorsPerDomain(),
                p.artifactsPerMonth(), p.seats(), p.historyMonths(), p.consultingMinutesPerMonth()}) {
            if (n != null) {
                numbers.add(n);
            }
        }
        if (numbers.stream().anyMatch(n -> n < 0)) {
            throw new IllegalStateException("plans.json: plan " + p.id() + " has a negative limit");
        }
    }

    public Optional<Plan> find(String planId) {
        return Optional.ofNullable(byId.get(planId));
    }

    /** The plan for {@code planId}; an unknown id is a configuration error, not a user error. */
    public Plan get(String planId) {
        return find(planId).orElseThrow(() -> new IllegalStateException("Unknown plan id: " + planId));
    }

    /** Reject with {@link PlanLimitExceededException} when the account would own more domains than allowed (REQ-12). */
    public void requireDomains(String planId, int domainsAfterCreate) {
        require(planId, "domains", get(planId).domains(), domainsAfterCreate);
    }

    /** Reject when a project would measure more languages than allowed (REQ-13). */
    public void requireLanguages(String planId, int languages) {
        require(planId, "languages", get(planId).languages(), languages);
    }

    /** Reject when the account would measure more prompts (each prompt is one language) than allowed. */
    public void requirePrompts(String planId, int promptsAfterCreate) {
        require(planId, "prompts", get(planId).prompts(), promptsAfterCreate);
    }

    private static void require(String planId, String limit, Integer allowed, int requested) {
        if (!Plan.within(allowed, requested)) {
            throw new PlanLimitExceededException(planId, limit, allowed);
        }
    }
}
