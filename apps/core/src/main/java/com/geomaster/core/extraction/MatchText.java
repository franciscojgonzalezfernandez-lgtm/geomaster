package com.geomaster.core.extraction;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Answer text and model output folded the same way before substring checks (extraction-analysis
 * REQ-7, REQ-8): Unicode NFC, lower case, typographic quotes and dashes as ASCII, Markdown emphasis
 * and code marks removed, hyphens as spaces and every run of whitespace as one space. Folding only
 * removes or replaces characters one for one, so an index in the folded text keeps the order of
 * first occurrences in the original.
 */
final class MatchText {

    private static final Pattern MARKUP = Pattern.compile("[*_`]+");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0\\u2007\\u202f]+", Pattern.UNICODE_CHARACTER_CLASS);
    /** Ellipses and quotes the model may wrap a quote in. */
    private static final Pattern WRAPPERS = Pattern.compile("^[\\s\"'.…]+|[\\s\"'.…]+$");

    private MatchText() {}

    static String fold(String raw) {
        if (raw == null) {
            return "";
        }
        String s = Normalizer.normalize(raw, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace('‘', '\'').replace('’', '\'').replace('‚', '\'')
                .replace('“', '"').replace('”', '"').replace('„', '"')
                .replace('«', '"').replace('»', '"')
                .replace('‐', '-').replace('‑', '-').replace('–', '-').replace('—', '-')
                .replace('-', ' ');
        s = MARKUP.matcher(s).replaceAll("");
        return SPACES.matcher(s).replaceAll(" ").strip();
    }

    /** A model quote folded, with surrounding quotes and ellipses dropped ("…great school." → "great school"). */
    static String foldQuote(String raw) {
        String s = fold(raw);
        String prev;
        do {
            prev = s;
            s = WRAPPERS.matcher(s).replaceAll("");
        } while (!s.equals(prev));
        return s;
    }
}
