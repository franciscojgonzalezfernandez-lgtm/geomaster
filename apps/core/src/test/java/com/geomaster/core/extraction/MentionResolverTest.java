package com.geomaster.core.extraction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Entity resolution and citation classes (extraction-analysis REQ-12..REQ-14). */
class MentionResolverTest {

    static final UUID OWN = UUID.randomUUID();
    static final UUID RIVAL = UUID.randomUUID();
    static final MentionResolver RESOLVER = new MentionResolver(List.of(
            new MentionResolver.BrandKeys(RIVAL, MentionResolver.COMPETITOR,
                    Set.of("skischuleflumserberg", "skischule"), Set.of("skischule"), Set.of("skischule.ch")),
            new MentionResolver.BrandKeys(OWN, MentionResolver.OWN,
                    Set.of("rideflumserberg", "ride"), Set.of("rideflumserberg"), Set.of("ride-flumserberg.ch"))));

    static MentionResolver.Extracted row(int ordinal, String name, String key, String domain, int position) {
        return new MentionResolver.Extracted(ordinal, name, key, domain, "positive", true, position);
    }

    @Test
    void namesAndDomainsOfOneBrandCollapseToTheEarliestPosition() {
        List<MentionResolver.Mention> m = RESOLVER.mentions(List.of(
                row(1, "Snowboard Heroes", "snowboardheroes", null, 2),
                row(2, "RIDE Flumserberg AG", "rideflumserberg", null, 1),
                row(3, "Ride", "ride", "ride-flumserberg.ch", 4),
                row(4, "Snowboardschule", "snowboardschule", "skischule.de", 3),
                row(5, "Snowboard Heroes", "snowboardheroes", null, 5)));

        assertEquals(3, m.size(), "one row per brand, untracked names too (REQ-13)");
        assertEquals(OWN, m.get(0).brandId());
        assertEquals("RIDE Flumserberg AG", m.get(0).extractedName());
        assertEquals(1, m.get(0).position());
        assertNull(m.get(1).brandId(), "Snowboard Heroes is untracked");
        assertEquals(2, m.get(1).position());
        assertEquals(RIVAL, m.get(2).brandId(), "matched by the stem of the domain the answer gave");
        assertEquals(4, m.get(2).ordinal());
        assertEquals(3, m.get(2).position(), "positions renumbered after collapsing");
    }

    @Test
    void citedDomainsAreOwnCompetitorOrThirdParty() {
        List<MentionResolver.Citation> c = RESOLVER.citations(List.of(
                new UrlScanner.Cited("https://ride-flumserberg.ch/kurse", "ride-flumserberg.ch", UrlScanner.PROVIDER),
                new UrlScanner.Cited("https://shop.ride-flumserberg.ch/", "shop.ride-flumserberg.ch", UrlScanner.TEXT),
                new UrlScanner.Cited("https://rideflumserberg.de/", "rideflumserberg.de", UrlScanner.TEXT),
                new UrlScanner.Cited("https://skischule.ch/", "skischule.ch", UrlScanner.PROVIDER),
                new UrlScanner.Cited("https://notskischule.ch/", "notskischule.ch", UrlScanner.PROVIDER),
                new UrlScanner.Cited("https://tripadvisor.ch/x", "tripadvisor.ch", UrlScanner.PROVIDER)));

        assertEquals(List.of("own", "own", "own", "competitor", "third_party", "third_party"),
                c.stream().map(MentionResolver.Citation::cls).toList());
        assertEquals(OWN, c.get(2).brandId(), "another TLD of the own domain stem");
        assertEquals(RIVAL, c.get(3).brandId());
        assertNull(c.get(5).brandId());
    }

    @Test
    void noBrandsMeansEverythingUntracked() {
        MentionResolver empty = new MentionResolver(List.of());
        assertNull(empty.mentions(List.of(row(1, "Ride", "ride", null, 1))).getFirst().brandId());
        assertEquals("third_party", empty.citations(List.of(
                new UrlScanner.Cited("https://a.ch", "a.ch", UrlScanner.TEXT))).getFirst().cls());
    }
}
