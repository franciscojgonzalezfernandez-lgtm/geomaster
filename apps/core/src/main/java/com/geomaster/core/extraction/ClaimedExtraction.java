package com.geomaster.core.extraction;

import java.util.UUID;

/**
 * An extraction claimed by this worker. {@code attempt} fences every write: a result lands only
 * while this worker still holds the lease. {@code scanId} is the latest scan that used the answer
 * (telemetry session only; the extraction itself is shared and tenant-free, REQ-6).
 */
public record ClaimedExtraction(UUID id, UUID llmCallId, int attempt, String answerText, UUID scanId) {}
