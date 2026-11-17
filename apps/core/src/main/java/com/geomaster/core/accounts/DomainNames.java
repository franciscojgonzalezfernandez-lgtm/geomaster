package com.geomaster.core.accounts;

import com.geomaster.core.api.InvalidInputException;

import java.net.IDN;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalises a user-entered domain (REQ-9): lowercase, strip scheme, credentials-free host only,
 * no path/query/fragment, no port, no {@code www.}, no trailing dot; IDNs become punycode. The
 * result must be a syntactically valid public hostname (not an IP), otherwise 422.
 */
public final class DomainNames {

    private static final Pattern SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*://");
    private static final Pattern LABEL = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern TLD = Pattern.compile("^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$");

    private DomainNames() {}

    public static String normalise(String input) {
        if (input == null || input.isBlank()) {
            throw invalid("domain is required");
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        s = SCHEME.matcher(s).replaceFirst("");
        int end = indexOfAny(s, '/', '?', '#');
        if (end >= 0) {
            s = s.substring(0, end);
        }
        if (s.contains("@")) {
            throw invalid("domain must not contain credentials");
        }
        int colon = s.lastIndexOf(':');
        if (colon >= 0) {
            String port = s.substring(colon + 1);
            if (!port.chars().allMatch(Character::isDigit)) {
                throw invalid("domain is not a valid hostname");
            }
            s = s.substring(0, colon);
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.startsWith("www.")) {
            s = s.substring(4);
        }
        try {
            s = IDN.toASCII(s, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            throw invalid("domain is not a valid hostname");
        }
        if (s.isEmpty() || s.length() > 253) {
            throw invalid("domain is not a valid hostname");
        }
        String[] labels = s.split("\\.", -1);
        if (labels.length < 2) {
            throw invalid("domain must include a top-level domain");
        }
        for (String label : labels) {
            if (!LABEL.matcher(label).matches()) {
                throw invalid("domain is not a valid hostname");
            }
        }
        if (!TLD.matcher(labels[labels.length - 1]).matches()) {
            throw invalid("domain is not a valid hostname");
        }
        return s;
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

    private static InvalidInputException invalid(String message) {
        return new InvalidInputException("domain", message);
    }
}
