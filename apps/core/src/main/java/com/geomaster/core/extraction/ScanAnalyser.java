package com.geomaster.core.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns a finished scan's extractions into per-project mentions and citation classes
 * (extraction-analysis REQ-12..REQ-18). A scan is analysed once it is finished and none of its done
 * units has an extraction still queued or running; failed extractions count as answers without data.
 * The first analysis also records the totals, adds the extraction cost to the scan's cost and writes
 * the extractor and resolver versions; {@link #reResolve} only rewrites mentions and citations with
 * the project's current brands (REQ-15). Each scan is handled in one transaction under a row lock
 * taken with SKIP LOCKED, so concurrent sweeps never write the same scan twice.
 */
public class ScanAnalyser {

    private static final Logger log = LoggerFactory.getLogger(ScanAnalyser.class);

    private final AnalysisRepository repo;
    private final TransactionTemplate tx;
    private final JsonMapper json = JsonMapper.builder().build();

    public ScanAnalyser(AnalysisRepository repo, TransactionTemplate tx) {
        this.repo = repo;
        this.tx = tx;
    }

    /** Analyse up to {@code limit} ready scans; returns how many were analysed. */
    public int analyseReady(ExtractorVersion version, int limit) {
        int n = 0;
        for (AnalysisRepository.ScanRef s : repo.readyToAnalyse(version.version(), limit)) {
            if (Boolean.TRUE.equals(tx.execute(st -> analyse(s, version)))) {
                n++;
            }
        }
        return n;
    }

    /** Re-resolve up to {@code limit} scans whose project's brands changed; returns how many. */
    public int reResolveStale(int limit) {
        int n = 0;
        for (AnalysisRepository.ScanRef s : repo.staleResolutions(limit)) {
            if (Boolean.TRUE.equals(tx.execute(st -> reResolve(s)))) {
                n++;
            }
        }
        return n;
    }

    private boolean analyse(AnalysisRepository.ScanRef s, ExtractorVersion version) {
        var locked = repo.lock(s, false);
        if (locked.isEmpty()) {
            return false;
        }
        List<AnalysisRepository.Unit> units = repo.doneUnits(s.scanId(), s.accountId(), version.version());
        if (units.stream().anyMatch(u -> "queued".equals(u.extractionStatus()) || "running".equals(u.extractionStatus()))) {
            return false; // a unit's extraction was retried after the sweep read the scan
        }
        resolve(s, locked.get().projectId(), units);

        int extracted = 0;
        int failed = 0;
        int rejections = 0;
        BigDecimal cost = BigDecimal.ZERO;
        Set<UUID> billed = new HashSet<>();
        for (AnalysisRepository.Unit u : units) {
            if ("done".equals(u.extractionStatus())) {
                extracted++;
                rejections += u.groundingRejections();
            } else if ("failed".equals(u.extractionStatus())) {
                failed++;
            }
            // Like the answer itself, a shared extraction is paid by the unit that made the fresh call.
            if (u.extractionId() != null && !u.cacheHit() && billed.add(u.extractionId())) {
                cost = cost.add(u.extractionCost());
            }
        }
        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("extractor", Map.of("version", version.version(), "fingerprint", version.fingerprint()));
        versions.put("resolver", MentionResolver.VERSION);
        repo.markAnalysed(s.scanId(), s.accountId(), locked.get().brandsVersion(), extracted, failed, rejections, cost,
                json.writeValueAsString(versions));
        log.debug("Scan {} analysed: {} extracted, {} failed, {} rejections", s.scanId(), extracted, failed, rejections);
        return true;
    }

    private boolean reResolve(AnalysisRepository.ScanRef s) {
        var locked = repo.lock(s, true);
        if (locked.isEmpty()) {
            return false;
        }
        String version = locked.get().extractorVersion();
        if (version != null) {
            resolve(s, locked.get().projectId(), repo.doneUnits(s.scanId(), s.accountId(), version));
        }
        repo.markReResolved(s.scanId(), s.accountId(), locked.get().brandsVersion());
        return true;
    }

    private void resolve(AnalysisRepository.ScanRef s, UUID projectId, List<AnalysisRepository.Unit> units) {
        MentionResolver resolver = new MentionResolver(repo.brandKeys(projectId, s.accountId()));
        repo.clearResolution(s.scanId(), s.accountId());
        for (AnalysisRepository.Unit u : units) {
            if ("done".equals(u.extractionStatus())) {
                for (MentionResolver.Mention m : resolver.mentions(repo.answerBrands(u.extractionId()))) {
                    repo.insertMention(s.scanId(), s.accountId(), u.unitId(), u.extractionId(), m);
                }
            }
            // Citations need no model, so they are classified even when the extraction failed (REQ-9).
            List<UrlScanner.Cited> cited = UrlScanner.scan(repo.providerUrls(u.llmCallId()), repo.answerText(u.llmCallId()));
            for (MentionResolver.Citation c : resolver.citations(cited)) {
                repo.insertCitation(s.scanId(), s.accountId(), u.unitId(), c);
            }
        }
    }
}
