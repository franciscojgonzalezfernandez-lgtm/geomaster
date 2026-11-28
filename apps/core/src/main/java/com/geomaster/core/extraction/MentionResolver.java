package com.geomaster.core.extraction;

import com.geomaster.core.brands.BrandNormalizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Maps one project's extracted brands and cited URLs to its own brand, its competitors or neither
 * (extraction-analysis REQ-12..REQ-14). Pure and deterministic: the same extractions and brand set
 * always give the same rows, so re-resolving after a brand edit is free (REQ-15).
 *
 * <ul>
 *   <li>A mention resolves when its name key, or the stem of the domain the answer gave, is one of a
 *       brand's keys (its name key plus every alias key, {@link BrandNormalizer}). Keys are unique per
 *       project, so the match is never ambiguous.</li>
 *   <li>Two names of one brand in one answer count once at the earliest position; untracked names
 *       collapse by key the same way. Positions are then renumbered 1..n (REQ-8, REQ-13).</li>
 *   <li>A cited domain is own or competitor when it is one of the brand's domain aliases or a
 *       subdomain of one, or when its stem is one of the brand's domain keys (another TLD);
 *       otherwise third party. The own brand wins a tie.</li>
 * </ul>
 */
public final class MentionResolver {

    public static final String VERSION = "res-1";
    public static final String OWN = "own";
    public static final String COMPETITOR = "competitor";
    public static final String THIRD_PARTY = "third_party";

    /** A project brand's matching data: all keys, its domain-alias keys and hosts. */
    public record BrandKeys(UUID brandId, String kind, Set<String> keys, Set<String> domainKeys, Set<String> hosts) {}

    /** A stored {@code answer_brand} row. */
    public record Extracted(int ordinal, String name, String normalized, String domain, String sentiment,
                            boolean recommended, int position) {}

    /** A resolved mention; {@code brandId} null means untracked. */
    public record Mention(int ordinal, UUID brandId, String extractedName, int position, String sentiment,
                          boolean recommended) {}

    /** A cited URL with its class; {@code brandId} null for third party. */
    public record Citation(String url, String domain, String source, String cls, UUID brandId) {}

    private final List<BrandKeys> brands;
    private final Map<String, BrandKeys> byKey = new HashMap<>();

    public MentionResolver(List<BrandKeys> brands) {
        List<BrandKeys> ordered = new ArrayList<>(brands);
        ordered.sort(Comparator.comparing(b -> !OWN.equals(b.kind()))); // own first: wins ties
        this.brands = List.copyOf(ordered);
        for (BrandKeys b : this.brands) {
            b.keys().forEach(k -> byKey.putIfAbsent(k, b));
        }
    }

    public List<Mention> mentions(List<Extracted> rows) {
        List<Extracted> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(Extracted::position).thenComparingInt(Extracted::ordinal));
        Set<String> seen = new HashSet<>();
        List<Mention> out = new ArrayList<>();
        for (Extracted e : sorted) {
            BrandKeys brand = byKey.get(e.normalized());
            if (brand == null && e.domain() != null && !e.domain().isBlank()) {
                brand = byKey.get(BrandNormalizer.domain(e.domain()));
            }
            String identity = brand != null ? "b:" + brand.brandId() : "u:" + e.normalized();
            if (seen.add(identity)) {
                out.add(new Mention(e.ordinal(), brand == null ? null : brand.brandId(), e.name(), out.size() + 1,
                        e.sentiment(), e.recommended()));
            }
        }
        return List.copyOf(out);
    }

    public List<Citation> citations(List<UrlScanner.Cited> cited) {
        List<Citation> out = new ArrayList<>(cited.size());
        for (UrlScanner.Cited c : cited) {
            BrandKeys owner = ownerOf(c.domain());
            out.add(new Citation(c.url(), c.domain(), c.source(),
                    owner == null ? THIRD_PARTY : owner.kind(), owner == null ? null : owner.brandId()));
        }
        return List.copyOf(out);
    }

    private BrandKeys ownerOf(String domain) {
        String host = domain.toLowerCase(Locale.ROOT);
        for (BrandKeys b : brands) {
            for (String h : b.hosts()) {
                if (host.equals(h) || host.endsWith("." + h)) {
                    return b;
                }
            }
        }
        String stem = BrandNormalizer.domain(host);
        for (BrandKeys b : brands) {
            if (b.domainKeys().contains(stem)) {
                return b;
            }
        }
        return null;
    }
}
