package com.geomaster.core.extraction;

import com.geomaster.core.tenant.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Reads a scan's mentions and citations (REQ-24); every query is scoped to the current account (NFR-05). */
@Repository
public class MentionsReadRepository {

    private record Header(OffsetDateTime analysedAt, String extractorVersion, String resolverVersion,
                          int extracted, int failed, int rejections) {}

    private record UnitRow(UUID unitId, UUID promptId, String engine, String locale, int sample, String extraction) {}

    private final JdbcClient db;

    public MentionsReadRepository(JdbcClient db) {
        this.db = db;
    }

    public Optional<ScanMentions> find(UUID scanId) {
        UUID account = TenantContext.accountId();
        Optional<Header> header = db.sql("""
                SELECT analysed_at, versions -> 'extractor' ->> 'version' AS extractor, versions ->> 'resolver' AS resolver,
                       answers_extracted, extractions_failed, grounding_rejections
                FROM scan WHERE id = :s AND account_id = :a
                """)
                .param("s", scanId).param("a", account)
                .query((rs, i) -> new Header(rs.getObject(1, OffsetDateTime.class), rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getInt(5), rs.getInt(6)))
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        Header h = header.get();

        List<UnitRow> units = db.sql("""
                SELECT u.id, u.prompt_id, u.engine, u.locale, u.sample, e.status
                FROM scan_unit u
                LEFT JOIN answer_extraction e ON e.llm_call_id = u.llm_call_id AND e.extractor_version = :version
                                             AND e.status IN ('done', 'failed')
                WHERE u.scan_id = :s AND u.account_id = :a AND u.status = 'done'
                ORDER BY u.engine, u.locale, u.prompt_id, u.sample
                """)
                .param("s", scanId).param("a", account)
                .param("version", h.analysedAt() == null ? "" : h.extractorVersion())
                .query((rs, i) -> new UnitRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getInt(5), rs.getString(6)))
                .list();

        Map<UUID, List<ScanMentions.Mention>> mentions = new HashMap<>();
        Map<UUID, List<ScanMentions.Untracked>> untracked = new HashMap<>();
        db.sql("""
                SELECT m.scan_unit_id, m.position, m.brand_id, b.kind, b.name AS brand_name, m.extracted_name,
                       m.sentiment, m.recommended, ab.evidence
                FROM scan_mention m
                JOIN answer_brand ab ON ab.extraction_id = m.extraction_id AND ab.ordinal = m.ordinal
                LEFT JOIN brand b ON b.id = m.brand_id AND b.account_id = m.account_id
                WHERE m.scan_id = :s AND m.account_id = :a
                ORDER BY m.scan_unit_id, m.position
                """)
                .param("s", scanId).param("a", account)
                .query((rs, i) -> {
                    UUID unit = rs.getObject("scan_unit_id", UUID.class);
                    UUID brand = rs.getObject("brand_id", UUID.class);
                    if (brand == null) {
                        untracked.computeIfAbsent(unit, k -> new ArrayList<>()).add(new ScanMentions.Untracked(
                                rs.getInt("position"), rs.getString("extracted_name"), rs.getString("sentiment"),
                                rs.getBoolean("recommended"), rs.getString("evidence")));
                    } else {
                        mentions.computeIfAbsent(unit, k -> new ArrayList<>()).add(new ScanMentions.Mention(
                                rs.getInt("position"), brand, rs.getString("kind"), rs.getString("brand_name"),
                                rs.getString("extracted_name"), rs.getString("sentiment"), rs.getBoolean("recommended"),
                                rs.getString("evidence")));
                    }
                    return unit;
                })
                .list();

        Map<UUID, List<ScanMentions.Citation>> citations = new HashMap<>();
        db.sql("""
                SELECT scan_unit_id, url, domain, source, class, brand_id FROM scan_citation
                WHERE scan_id = :s AND account_id = :a
                ORDER BY scan_unit_id, source, domain, url
                """)
                .param("s", scanId).param("a", account)
                .query((rs, i) -> {
                    UUID unit = rs.getObject("scan_unit_id", UUID.class);
                    citations.computeIfAbsent(unit, k -> new ArrayList<>()).add(new ScanMentions.Citation(
                            rs.getString("url"), rs.getString("domain"), rs.getString("source"), rs.getString("class"),
                            rs.getObject("brand_id", UUID.class)));
                    return unit;
                })
                .list();

        List<ScanMentions.Answer> answers = units.stream().map(u -> new ScanMentions.Answer(
                u.unitId(), u.promptId(), u.engine(), u.locale(), u.sample(), u.extraction(),
                List.copyOf(mentions.getOrDefault(u.unitId(), List.of())),
                List.copyOf(untracked.getOrDefault(u.unitId(), List.of())),
                List.copyOf(citations.getOrDefault(u.unitId(), List.of())))).toList();
        return Optional.of(new ScanMentions(scanId, h.analysedAt(), h.extractorVersion(), h.resolverVersion(),
                h.extracted(), h.failed(), h.rejections(), answers));
    }
}
