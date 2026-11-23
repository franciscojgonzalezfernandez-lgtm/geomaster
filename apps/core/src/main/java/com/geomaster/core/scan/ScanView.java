package com.geomaster.core.scan;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A scan as its account sees it (REQ-17): status, progress per engine and cost. */
public record ScanView(
        UUID id,
        UUID projectId,
        String status,
        String statusReason,
        String trigger,
        int samples,
        int unitsTotal,
        int unitsDone,
        int unitsFailed,
        BigDecimal costChf,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        List<EngineProgress> engines) {

    /** Progress of one engine; {@code model} is the one the scan was planned with. */
    public record EngineProgress(String engine, String model, int total, int done, int failed, int cacheHits) {}
}
