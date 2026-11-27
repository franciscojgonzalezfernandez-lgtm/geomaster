package com.geomaster.core.brands;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Matching keys for brand names and domains (extraction-analysis REQ-12). Keeps the contract
 * written in {@code V6__extraction_analysis.sql}: accents stripped, lower-case, trailing legal
 * suffixes dropped, every non-alphanumeric character removed; for domains, scheme, {@code www.},
 * path and the last label (TLD) are dropped first. So "Ride Flumserberg", "RIDE-Flumserberg AG"
 * and "rideflumserberg.ch" all share the key {@code rideflumserberg}.
 *
 * <p>A name that has no Latin letters or digits left has no key ({@code ""}); callers reject it.
 */
public final class BrandNormalizer {

    /** Trailing company-form tokens, after punctuation is split off ("S.L." → "s", "l" → "sl"). */
    private static final Set<String> LEGAL_SUFFIXES = Set.of(
            "ag", "gmbh", "sa", "sarl", "sl", "sas", "srl", "spa", "ltd", "limited", "inc", "llc", "plc",
            "kg", "ohg", "ug", "se", "bv", "nv", "corp", "co");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*://");

    private BrandNormalizer() {}

    /** Key of a brand name as written, e.g. "Skischule Flumserberg GmbH" → "skischuleflumserberg". */
    public static String name(String raw) {
        if (raw == null) {
            return "";
        }
        List<String> tokens = new ArrayList<>(Arrays.asList(NON_ALNUM.split(fold(raw))));
        tokens.removeIf(String::isEmpty);
        boolean dropped = true;
        while (dropped && tokens.size() > 1) {
            dropped = false;
            int n = tokens.size();
            if (LEGAL_SUFFIXES.contains(tokens.get(n - 1))) {
                tokens.remove(n - 1);
                dropped = true;
            } else if (n > 2 && tokens.get(n - 1).length() == 1 && tokens.get(n - 2).length() == 1
                    && LEGAL_SUFFIXES.contains(tokens.get(n - 2) + tokens.get(n - 1))) {
                tokens.remove(n - 1);
                tokens.remove(n - 2);
                dropped = true;
            }
        }
        return String.join("", tokens);
    }

    /** Key of a domain or URL: its stem without scheme, www., path or TLD ("https://www.ride-flumserberg.ch/de" → "rideflumserberg"). */
    public static String domain(String raw) {
        if (raw == null) {
            return "";
        }
        String s = SCHEME.matcher(raw.trim().toLowerCase(Locale.ROOT)).replaceFirst("");
        int end = indexOfAny(s, '/', '?', '#', ':');
        if (end >= 0) {
            s = s.substring(0, end);
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        int dot = s.lastIndexOf('.');
        if (dot > 0) {
            s = s.substring(0, dot);
        }
        return NON_ALNUM.matcher(fold(s)).replaceAll("");
    }

    /** Lower-case without accents; ß and common ligatures spelled out so they survive the ASCII filter. */
    private static String fold(String s) {
        String lower = s.toLowerCase(Locale.ROOT)
                .replace("ß", "ss").replace("æ", "ae").replace("œ", "oe").replace("ø", "o").replace("ł", "l");
        return MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }

    private static int indexOfAny(String s, char... chars) {
        int min = -1;
        for (char c : chars) {
            int i = s.indexOf(c);
            if (i >= 0 && (min < 0 || i < min)) {
                min = i;
            }
        }
        return min;
    }
}
