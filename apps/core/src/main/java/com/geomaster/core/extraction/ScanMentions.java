package com.geomaster.core.extraction;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A scan's resolved mentions as its account sees them (extraction-analysis REQ-24). Before the scan
 * is analysed {@code analysedAt} is null and every answer has empty lists.
 */
public record ScanMentions(
        UUID scanId,
        OffsetDateTime analysedAt,
        String extractorVersion,
        String resolverVersion,
        int answersExtracted,
        int extractionsFailed,
        int groundingRejections,
        List<Answer> answers) {

    /**
     * One answered unit. {@code extraction} is {@code done}, {@code failed}, or null when the scan is
     * not analysed yet or the answer was never extracted.
     */
    public record Answer(UUID unitId, UUID promptId, String engine, String locale, int sample, String extraction,
                         List<Mention> mentions, List<Untracked> untracked, List<Citation> citations) {}

    /** A tracked brand in the answer; {@code brandName} is the brand's current name, even if since deleted. */
    public record Mention(int position, UUID brandId, String brandKind, String brandName, String extractedName,
                          String sentiment, boolean recommended, String evidence) {}

    /** A brand the answer names that the project does not track. */
    public record Untracked(int position, String name, String sentiment, boolean recommended, String evidence) {}

    /** A cited URL; {@code domainClass} is own, competitor or third_party. */
    public record Citation(String url, String domain, String source, String domainClass, UUID brandId) {}
}
