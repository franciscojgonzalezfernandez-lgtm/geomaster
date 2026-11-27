package com.geomaster.core.brands;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** An active brand of a project (own or competitor) with its active aliases. */
public record Brand(UUID id, UUID projectId, String kind, String name, List<Alias> aliases, OffsetDateTime createdAt) {

    public static final String OWN = "own";
    public static final String COMPETITOR = "competitor";

    /** A name or domain the brand is also written as; {@code normalized} is the matching key. */
    public record Alias(UUID id, UUID brandId, String kind, String value, String normalized) {}

    public Brand withAliases(List<Alias> aliases) {
        return new Brand(id, projectId, kind, name, List.copyOf(aliases), createdAt);
    }
}
