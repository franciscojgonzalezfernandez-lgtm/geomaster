package com.geomaster.core.scan;

import com.geomaster.core.providers.Engine;

import java.util.List;
import java.util.UUID;

/** What a started scan will ask: every prompt × engine × language × sample becomes one unit (REQ-3). */
public record ScanPlan(UUID scanId, List<Engine> engines, List<String> locales, int prompts, int samples, int units) {}
