package com.geomaster.core.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cited URLs from provider citations plus literal URLs in the text (REQ-9). */
class UrlScannerTest {

    static List<String> rows(List<UrlScanner.Cited> cited) {
        return cited.stream().map(c -> c.source() + " " + c.domain() + " " + c.url()).toList();
    }

    @Test
    void mergesProviderCitationsAndLiteralUrls() {
        String answer = """
                See [Ride](https://rideflumserberg.ch/kurse?utm_source=openai) or www.skischule-flumserberg.ch.
                More: https://de.wikipedia.org/wiki/Flumserberg_(Ort), and (https://example.com/a).
                Not URLs: ftp://x.ch, mailto:a@b.ch, https://localhost/x.
                """;
        List<UrlScanner.Cited> cited = UrlScanner.scan(List.of(
                "https://www.rideflumserberg.ch/kurse/?utm_medium=x",
                "https://booking.com/hotel"), answer);
        assertEquals(List.of(
                "provider rideflumserberg.ch https://www.rideflumserberg.ch/kurse/?utm_medium=x",
                "provider booking.com https://booking.com/hotel",
                "text skischule-flumserberg.ch https://www.skischule-flumserberg.ch",
                "text de.wikipedia.org https://de.wikipedia.org/wiki/Flumserberg_(Ort)",
                "text example.com https://example.com/a"), rows(cited));
    }

    @Test
    void skipsBlankHostlessAndOverlongUrls() {
        String longUrl = "https://example.com/" + "a".repeat(UrlScanner.MAX_URL);
        assertEquals(List.of(), UrlScanner.scan(List.of("", "not a url", longUrl), null));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            https://Example.com/A/#top            | https://example.com/A
            http://www.example.com/a?utm_x=1&id=2 | https://example.com/a?id=2
            https://example.com                   | https://example.com
            """)
    void keys(String url, String key) {
        assertEquals(key, UrlScanner.key(url));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            https://x.ch/a.         | https://x.ch/a
            https://x.ch/a),        | https://x.ch/a
            https://x.ch/f_(o)      | https://x.ch/f_(o)
            https://x.ch/**         | https://x.ch/
            """)
    void trims(String raw, String url) {
        assertEquals(url, UrlScanner.trim(raw));
    }

    @Test
    void domains() {
        assertEquals("rideflumserberg.ch", UrlScanner.domainOf("https://www.RideFlumserberg.ch./kurse?x=1"));
        assertEquals("", UrlScanner.domainOf("not a url"));
    }
}
