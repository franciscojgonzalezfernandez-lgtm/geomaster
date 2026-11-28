package com.geomaster.core.extraction;

import com.geomaster.core.brands.BrandNormalizer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reads and writes for scan analysis (extraction-analysis REQ-12..REQ-18). Runs in the background
 * with no request tenant, so every tenant-owned query takes the scan's {@code account_id}
 * explicitly and filters by it (NFR-05).
 */
@Repository
public class AnalysisRepository {

    /** A scan to analyse or re-resolve. */
    public record ScanRef(UUID scanId, UUID accountId) {}

    /** A locked scan: its project, the project's brands version and the extractor version it was analysed with. */
    public record LockedScan(UUID projectId, int brandsVersion, String extractorVersion) {}

    /** A done unit of the scan with its extraction for one extractor version (null fields when none). */
    public record Unit(UUID unitId, UUID llmCallId, boolean cacheHit, UUID extractionId, String extractionStatus,
                       BigDecimal extractionCost, int groundingRejections) {}

    private final JdbcClient db;

    public AnalysisRepository(JdbcClient db) {
        this.db = db;
    }

    /** Finished, unanalysed scans none of whose done units has an extraction still queued or running. */
    public List<ScanRef> readyToAnalyse(String extractorVersion, int limit) {
        return db.sql("""
                SELECT s.id, s.account_id FROM scan s
                WHERE s.analysed_at IS NULL AND s.finished_at IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM scan_unit u
                                  JOIN answer_extraction e ON e.llm_call_id = u.llm_call_id
                                                          AND e.extractor_version = :version
                                  WHERE u.scan_id = s.id AND u.status = 'done'
                                    AND e.status IN ('queued', 'running'))
                ORDER BY s.finished_at
                LIMIT :limit
                """)
                .param("version", extractorVersion).param("limit", limit)
                .query((rs, i) -> new ScanRef(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                .list();
    }

    /** Analysed scans resolved with an older brand set than their project's current one (REQ-15). */
    public List<ScanRef> staleResolutions(int limit) {
        return db.sql("""
                SELECT s.id, s.account_id FROM scan s
                JOIN project p ON p.id = s.project_id AND p.account_id = s.account_id
                WHERE s.analysed_at IS NOT NULL AND s.brands_version IS DISTINCT FROM p.brands_version
                ORDER BY s.analysed_at
                LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, i) -> new ScanRef(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                .list();
    }

    /**
     * Lock the scan for this transaction, skipping it when another instance holds it.
     *
     * @param analysed false to lock an unanalysed scan, true an analysed one
     */
    public Optional<LockedScan> lock(ScanRef s, boolean analysed) {
        return db.sql("""
                SELECT s.project_id, p.brands_version, s.versions -> 'extractor' ->> 'version' AS extractor
                FROM scan s JOIN project p ON p.id = s.project_id AND p.account_id = s.account_id
                WHERE s.id = :scan AND s.account_id = :account AND (s.analysed_at IS NOT NULL) = :analysed
                FOR UPDATE OF s SKIP LOCKED
                """)
                .param("scan", s.scanId()).param("account", s.accountId()).param("analysed", analysed)
                .query((rs, i) -> new LockedScan(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3)))
                .optional();
    }

    /** The project's active brands as matching data (REQ-3: deleted brands and aliases no longer resolve). */
    public List<MentionResolver.BrandKeys> brandKeys(UUID projectId, UUID accountId) {
        Map<UUID, String> kinds = new LinkedHashMap<>();
        Map<UUID, Set<String>> keys = new HashMap<>();
        Map<UUID, Set<String>> domainKeys = new HashMap<>();
        Map<UUID, Set<String>> hosts = new HashMap<>();
        db.sql("""
                SELECT id, kind, name FROM brand
                WHERE project_id = :p AND account_id = :a AND deleted_at IS NULL
                """)
                .param("p", projectId).param("a", accountId)
                .query((rs, i) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    kinds.put(id, rs.getString("kind"));
                    keys.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(BrandNormalizer.name(rs.getString("name")));
                    return id;
                })
                .list();
        db.sql("""
                SELECT brand_id, kind, value, normalized FROM brand_alias
                WHERE project_id = :p AND account_id = :a AND deleted_at IS NULL
                """)
                .param("p", projectId).param("a", accountId)
                .query((rs, i) -> {
                    UUID id = rs.getObject("brand_id", UUID.class);
                    keys.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(rs.getString("normalized"));
                    if ("domain".equals(rs.getString("kind"))) {
                        domainKeys.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(rs.getString("normalized"));
                        hosts.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(rs.getString("value").toLowerCase(Locale.ROOT));
                    }
                    return id;
                })
                .list();
        List<MentionResolver.BrandKeys> out = new ArrayList<>();
        kinds.forEach((id, kind) -> {
            Set<String> k = keys.getOrDefault(id, Set.of());
            k.remove("");
            out.add(new MentionResolver.BrandKeys(id, kind, k, domainKeys.getOrDefault(id, Set.of()),
                    hosts.getOrDefault(id, Set.of())));
        });
        return out;
    }

    public List<Unit> doneUnits(UUID scanId, UUID accountId, String extractorVersion) {
        return db.sql("""
                SELECT u.id, u.llm_call_id, u.cache_hit, e.id AS extraction_id, e.status, e.cost_chf,
                       e.grounding_rejections
                FROM scan_unit u
                LEFT JOIN answer_extraction e ON e.llm_call_id = u.llm_call_id AND e.extractor_version = :version
                WHERE u.scan_id = :scan AND u.account_id = :account AND u.status = 'done'
                ORDER BY u.id
                """)
                .param("scan", scanId).param("account", accountId).param("version", extractorVersion)
                .query((rs, i) -> new Unit(rs.getObject("id", UUID.class), rs.getObject("llm_call_id", UUID.class),
                        rs.getBoolean("cache_hit"), rs.getObject("extraction_id", UUID.class), rs.getString("status"),
                        rs.getBigDecimal("cost_chf"), rs.getInt("grounding_rejections")))
                .list();
    }

    public List<MentionResolver.Extracted> answerBrands(UUID extractionId) {
        return db.sql("""
                SELECT ordinal, name, normalized, domain, sentiment, recommended, position
                FROM answer_brand WHERE extraction_id = :id ORDER BY position
                """)
                .param("id", extractionId)
                .query(MentionResolver.Extracted.class)
                .list();
    }

    public String answerText(UUID llmCallId) {
        return db.sql("SELECT answer_text FROM llm_call WHERE id = :id").param("id", llmCallId)
                .query(String.class).single();
    }

    public List<String> providerUrls(UUID llmCallId) {
        return db.sql("SELECT url FROM llm_call_citation WHERE llm_call_id = :id ORDER BY position")
                .param("id", llmCallId).query(String.class).list();
    }

    /** Replace the scan's mentions and citations (resolution is a pure function, so rewrite is safe). */
    public void clearResolution(UUID scanId, UUID accountId) {
        db.sql("DELETE FROM scan_mention WHERE scan_id = :s AND account_id = :a")
                .param("s", scanId).param("a", accountId).update();
        db.sql("DELETE FROM scan_citation WHERE scan_id = :s AND account_id = :a")
                .param("s", scanId).param("a", accountId).update();
    }

    public void insertMention(UUID scanId, UUID accountId, UUID unitId, UUID extractionId, MentionResolver.Mention m) {
        db.sql("""
                INSERT INTO scan_mention (account_id, scan_id, scan_unit_id, extraction_id, ordinal, brand_id,
                                          extracted_name, position, sentiment, recommended, resolver_version)
                VALUES (:a, :s, :u, :e, :ordinal, :brand, :name, :position, :sentiment, :recommended, :version)
                """)
                .param("a", accountId).param("s", scanId).param("u", unitId).param("e", extractionId)
                .param("ordinal", m.ordinal()).param("brand", m.brandId()).param("name", m.extractedName())
                .param("position", m.position()).param("sentiment", m.sentiment()).param("recommended", m.recommended())
                .param("version", MentionResolver.VERSION)
                .update();
    }

    public void insertCitation(UUID scanId, UUID accountId, UUID unitId, MentionResolver.Citation c) {
        db.sql("""
                INSERT INTO scan_citation (account_id, scan_id, scan_unit_id, url, domain, source, class, brand_id,
                                           resolver_version)
                VALUES (:a, :s, :u, :url, :domain, :source, :class, :brand, :version)
                ON CONFLICT (scan_unit_id, url) DO NOTHING
                """)
                .param("a", accountId).param("s", scanId).param("u", unitId).param("url", c.url())
                .param("domain", c.domain()).param("source", c.source()).param("class", c.cls())
                .param("brand", c.brandId()).param("version", MentionResolver.VERSION)
                .update();
    }

    /** First analysis: totals, extraction cost added to the scan's cost, versions (REQ-16..REQ-18). */
    public void markAnalysed(UUID scanId, UUID accountId, int brandsVersion, int extracted, int failed,
                             int rejections, BigDecimal extractionCost, String versionsJson) {
        db.sql("""
                UPDATE scan
                SET analysed_at = now(), brands_version = :bv, answers_extracted = :extracted,
                    extractions_failed = :failed, grounding_rejections = :rejections,
                    extraction_cost_chf = :cost, cost_chf = cost_chf + :cost,
                    versions = versions || CAST(:versions AS jsonb)
                WHERE id = :s AND account_id = :a AND analysed_at IS NULL
                """)
                .param("bv", brandsVersion).param("extracted", extracted).param("failed", failed)
                .param("rejections", rejections).param("cost", extractionCost).param("versions", versionsJson)
                .param("s", scanId).param("a", accountId)
                .update();
    }

    public void markReResolved(UUID scanId, UUID accountId, int brandsVersion) {
        db.sql("UPDATE scan SET brands_version = :bv WHERE id = :s AND account_id = :a")
                .param("bv", brandsVersion).param("s", scanId).param("a", accountId).update();
    }
}
