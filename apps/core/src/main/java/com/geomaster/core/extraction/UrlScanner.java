package com.geomaster.core.extraction;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cited URLs of one answer without an LLM call (extraction-analysis REQ-9): the provider citations
 * stored with the answer, then URLs written literally in the text ({@code http(s)://…} or
 * {@code www.…}). The same page found twice counts once, keeping the provider's copy and the first
 * position: URLs compare without fragment, {@code utm_*} parameters, trailing slash and {@code www.},
 * with http as https and the scheme and host in lower case. URLs without a host or longer than {@link #MAX_URL} are skipped.
 */
public final class UrlScanner {

    public static final String PROVIDER = "provider";
    public static final String TEXT = "text";
    static final int MAX_URL = 2048;

    /** A cited URL with its domain (lower case, no {@code www.}) and where it came from. */
    public record Cited(String url, String domain, String source) {}

    private static final Pattern LITERAL = Pattern.compile(
            "(?i)\\b(?:https?://|www\\.)[^\\s<>\"'`\\[\\]{}|\\\\^]+");
    private static final String TRAILING = ".,;:!?*_~'\"";

    private UrlScanner() {}

    public static List<Cited> scan(List<String> providerUrls, String answerText) {
        Map<String, Cited> byKey = new LinkedHashMap<>();
        for (String url : providerUrls) {
            add(byKey, url == null ? "" : url.strip(), PROVIDER);
        }
        if (answerText != null) {
            Matcher m = LITERAL.matcher(answerText);
            while (m.find()) {
                String url = trim(m.group());
                add(byKey, url.regionMatches(true, 0, "www.", 0, 4) ? "https://" + url : url, TEXT);
            }
        }
        return List.copyOf(byKey.values());
    }

    /** Lower-case host without {@code www.}, or "" when the URL has none. */
    public static String domainOf(String url) {
        try {
            String host = URI.create(url).getHost();
            if (host == null) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            while (host.endsWith(".")) {
                host = host.substring(0, host.length() - 1);
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static void add(Map<String, Cited> byKey, String url, String source) {
        if (url.isEmpty() || url.length() > MAX_URL) {
            return;
        }
        String domain = domainOf(url);
        if (domain.isEmpty() || !domain.contains(".")) {
            return;
        }
        byKey.putIfAbsent(key(url), new Cited(url, domain, source));
    }

    /** Drops sentence punctuation and a closing bracket that does not belong to the URL. */
    static String trim(String url) {
        String u = url;
        boolean changed = true;
        while (changed && !u.isEmpty()) {
            changed = false;
            char last = u.charAt(u.length() - 1);
            if (TRAILING.indexOf(last) >= 0) {
                u = u.substring(0, u.length() - 1);
                changed = true;
            } else if (last == ')' && count(u, '(') < count(u, ')')) {
                u = u.substring(0, u.length() - 1);
                changed = true;
            }
        }
        return u;
    }

    static String key(String url) {
        String u = url;
        int hash = u.indexOf('#');
        if (hash >= 0) {
            u = u.substring(0, hash);
        }
        int q = u.indexOf('?');
        String query = "";
        if (q >= 0) {
            List<String> keep = new ArrayList<>();
            for (String p : u.substring(q + 1).split("&")) {
                if (!p.isEmpty() && !p.toLowerCase(Locale.ROOT).startsWith("utm_")) {
                    keep.add(p);
                }
            }
            query = keep.isEmpty() ? "" : "?" + String.join("&", keep);
            u = u.substring(0, q);
        }
        int hostStart = u.indexOf("://");
        int pathStart = hostStart < 0 ? -1 : u.indexOf('/', hostStart + 3);
        String origin = pathStart < 0 ? u : u.substring(0, pathStart);
        String path = pathStart < 0 ? "" : u.substring(pathStart);
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        origin = origin.toLowerCase(Locale.ROOT).replaceFirst("^(https?://)www\\.", "$1");
        return origin.replaceFirst("^http://", "https://") + path + query;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }
}
