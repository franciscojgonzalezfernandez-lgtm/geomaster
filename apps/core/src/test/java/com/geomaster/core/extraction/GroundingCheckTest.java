package com.geomaster.core.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Grounding (REQ-7) and positions (REQ-8) on answer texts in the four languages. */
class GroundingCheckTest {

    static ExtractedBrand brand(int ordinal, String name, String domain, String evidence) {
        return new ExtractedBrand(ordinal, name, domain, "neutral", false, evidence);
    }

    static List<String> names(GroundingCheck.Result r) {
        return r.kept().stream().map(g -> g.position() + ":" + g.brand().name()).toList();
    }

    @Test
    void positionsFollowTheTextNotTheModelOrder() {
        String answer = """
                Für Snowboardkurse in Flumserberg gibt es zwei gute Optionen:
                1. **Skischule Flumserberg** – grosse Schule mit vielen Gruppen.
                2. **Ride Flumserberg** – spezialisiert auf Snowboard, sehr gute Bewertungen.
                """;
        GroundingCheck.Result r = GroundingCheck.apply(answer, List.of(
                brand(1, "Ride Flumserberg", "rideflumserberg.ch", "spezialisiert auf Snowboard, sehr gute Bewertungen"),
                brand(2, "Skischule Flumserberg", null, "Skischule Flumserberg – grosse Schule")));
        assertEquals(List.of("1:Skischule Flumserberg", "2:Ride Flumserberg"), names(r));
        assertEquals(0, r.rejections());
    }

    @Test
    void dropsInventedBrandsAndInventedQuotes() {
        String answer = "Ride Flumserberg ist eine Snowboardschule in Flumserberg.";
        GroundingCheck.Result r = GroundingCheck.apply(answer, List.of(
                brand(1, "Ride Flumserberg", null, "eine Snowboardschule in Flumserberg"),
                brand(2, "Snowboard Heroes", null, "eine Snowboardschule in Flumserberg"), // name not in text
                brand(3, "Ride Flumserberg", null, "die beste Schule der Schweiz"),      // quote not in text
                brand(4, "Ride", null, "")));                                            // empty quote
        assertEquals(List.of("1:Ride Flumserberg"), names(r));
        assertEquals(3, r.rejections());
    }

    @Test
    void domainGroundsABrandWhoseNameIsNotWritten() {
        String answer = "Infos und Buchung auf https://www.rideflumserberg.ch/de/kurse.";
        GroundingCheck.Result r = GroundingCheck.apply(answer, List.of(
                brand(1, "Ride Flumserberg", "https://rideflumserberg.ch", "Infos und Buchung auf")));
        assertEquals(List.of("1:Ride Flumserberg"), names(r));
    }

    @Test
    void nameMustBeAWholeWord() {
        String answer = "You can override the default settings in the app.";
        GroundingCheck.Result r = GroundingCheck.apply(answer, List.of(brand(1, "Ride", null, "override the default")));
        assertEquals(0, r.kept().size());
        assertEquals(1, r.rejections());
    }

    @Test
    void tiesKeepTheModelOrder() {
        String answer = "Hotel Suisse and Hotel Alpina share a lobby.";
        GroundingCheck.Result r = GroundingCheck.apply(answer, List.of(
                brand(2, "Hotel Alpina", null, "Hotel Suisse and Hotel Alpina share a lobby"),
                brand(1, "Hotel Suisse", null, "Hotel Suisse and Hotel Alpina share a lobby")));
        assertEquals(List.of("1:Hotel Suisse", "2:Hotel Alpina"), names(r));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            case and accents in NFD    | L'Hôtel Suisse propose des cours.     | l'hôtel suisse     | L'HÔTEL SUISSE propose
            typographic quotes         | Viajes Martín, «la mejor opción».     | Viajes Martín      | “la mejor opción”
            hyphens and dashes         | Ride-Flumserberg — top for beginners. | Ride Flumserberg   | Ride Flumserberg - top for beginners
            markdown and line breaks   | **Café Zürich**\\nserves great coffee | café zürich        | Café Zürich serves great
            ellipsis around the quote  | Acme is fast, cheap and reliable.     | Acme               | …fast, cheap and reliable…
            non-breaking space         | Hotel\u00a0Alpina is open.     | Hotel Alpina       | Hotel Alpina is open
            """)
    void foldingMatchesVariants(String label, String answer, String name, String evidence) {
        String text = label.startsWith("case") ? java.text.Normalizer.normalize(answer, java.text.Normalizer.Form.NFD) : answer;
        text = text.replace("\\n", "\n").replace("\\u00a0", "\u00a0");
        GroundingCheck.Result r = GroundingCheck.apply(text, List.of(brand(1, name, null, evidence)));
        assertEquals(1, r.kept().size(), label);
    }
}
