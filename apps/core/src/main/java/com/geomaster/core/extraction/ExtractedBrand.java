package com.geomaster.core.extraction;

/**
 * One brand as the extractor model returned it (schema v1, extraction-analysis REQ-5), before the
 * grounding check. {@code ordinal} is the 1-based model output order; {@code domain} may be null.
 */
public record ExtractedBrand(int ordinal, String name, String domain, String sentiment, boolean recommended, String evidence) {}
