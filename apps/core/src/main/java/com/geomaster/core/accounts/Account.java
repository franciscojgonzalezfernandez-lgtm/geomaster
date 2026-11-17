package com.geomaster.core.accounts;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A tenant. {@code planId} matches an id in {@code packages/plans/plans.json} (ADR-0010). */
public record Account(UUID id, String name, String planId, OffsetDateTime createdAt) {}
