package com.geomaster.core.extraction;

import java.net.URI;
import java.util.Locale;

/**
 * Where a brand first occurs in a folded answer text (extraction-analysis REQ-8). Names and domains
 * match as whole words, so "Ride" is not found inside "override"; a quote matches as a plain
 * substring. Positions are ranked from these indexes, never from the model's output order.
 */
final class PositionLocator {

    static final int ABSENT = -1;

    private PositionLocator() {}

    /** First index of {@code name} in {@code text} as a whole word, or {@link #ABSENT}. Both already folded. */
    static int firstWord(String text, String name) {
        if (name.isEmpty()) {
            return ABSENT;
        }
        for (int i = text.indexOf(name); i >= 0; i = text.indexOf(name, i + 1)) {
            if (boundary(text, i - 1, true) && boundary(text, i + name.length(), false)) {
                return i;
            }
        }
        return ABSENT;
    }

    /** First index of a quote in {@code text}, or {@link #ABSENT}. Both already folded. */
    static int firstQuote(String text, String quote) {
        return quote.isEmpty() ? ABSENT : text.indexOf(quote);
    }

    /** First index of the brand's name or domain host (scheme, {@code www.} and path dropped), or {@link #ABSENT}. */
    static int firstMention(String text, ExtractedBrand brand) {
        int byName = firstWord(text, MatchText.fold(brand.name()));
        int byDomain = firstWord(text, MatchText.fold(host(brand.domain())));
        return earliest(byName, byDomain);
    }

    static int earliest(int a, int b) {
        if (a == ABSENT) {
            return b;
        }
        return b == ABSENT ? a : Math.min(a, b);
    }

    static String host(String domain) {
        if (domain == null || domain.isBlank()) {
            return "";
        }
        String d = domain.strip().toLowerCase(Locale.ROOT);
        try {
            String h = URI.create(d.contains("://") ? d : "https://" + d).getHost();
            if (h != null) {
                d = h;
            }
        } catch (IllegalArgumentException ignored) {
            // keep the raw value; it simply will not match
        }
        while (d.endsWith(".")) {
            d = d.substring(0, d.length() - 1);
        }
        return d.startsWith("www.") ? d.substring(4) : d;
    }

    private static boolean boundary(String text, int at, boolean before) {
        if (at < 0 || at >= text.length()) {
            return true;
        }
        int cp = before ? text.codePointBefore(at + 1) : text.codePointAt(at);
        return !Character.isLetterOrDigit(cp);
    }
}
