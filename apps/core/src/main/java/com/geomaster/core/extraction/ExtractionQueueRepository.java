package com.geomaster.core.extraction;

import com.geomaster.core.providers.CostCalculator;
import com.geomaster.core.providers.StructuredResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The {@code answer_extraction} work queue (ADR-0004, extraction-analysis REQ-4, REQ-10, REQ-16).
 * Extractions are shared across accounts (REQ-6), so this is system code with no tenant filter; every
 * write is fenced by the row's id, {@code running} status and attempt number.
 */
@Repository
public class ExtractionQueueRepository {

    /** A grounded brand to store with a done extraction. */
    public record BrandRow(int ordinal, String name, String normalized, String domain, String sentiment,
                           boolean recommended, String evidence, int position) {}

    private final JdbcClient db;

    public ExtractionQueueRepository(JdbcClient db) {
        this.db = db;
    }

    /** Claim queued extractions whose backoff passed and running ones whose lease expired, with SKIP LOCKED. */
    @Transactional
    public List<ClaimedExtraction> claim(String extractorVersion, int limit, Duration lease) {
        return db.sql("""
                WITH claimable AS (
                    SELECT id FROM answer_extraction
                    WHERE extractor_version = :version
                      AND ((status = 'queued' AND next_attempt_at <= now())
                        OR (status = 'running' AND locked_until < now()))
                    ORDER BY next_attempt_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                ), claimed AS (
                    UPDATE answer_extraction e
                    SET status = 'running',
                        locked_until = now() + make_interval(secs => :leaseSeconds),
                        attempts = e.attempts + 1
                    FROM claimable c
                    WHERE e.id = c.id
                    RETURNING e.id, e.llm_call_id, e.attempts
                )
                SELECT c.id, c.llm_call_id, c.attempts, l.answer_text,
                       (SELECT u.scan_id FROM scan_unit u WHERE u.llm_call_id = c.llm_call_id
                        ORDER BY u.finished_at DESC NULLS LAST LIMIT 1) AS scan_id
                FROM claimed c JOIN llm_call l ON l.id = c.llm_call_id
                """)
                .param("version", extractorVersion)
                .param("limit", limit)
                .param("leaseSeconds", (double) lease.toSeconds())
                .query((rs, i) -> new ClaimedExtraction(
                        rs.getObject("id", UUID.class),
                        rs.getObject("llm_call_id", UUID.class),
                        rs.getInt("attempts"),
                        rs.getString("answer_text"),
                        rs.getObject("scan_id", UUID.class)))
                .list();
    }

    /** Store the grounded brands and the call's figures; false when the lease was lost. */
    @Transactional
    public boolean markDone(ClaimedExtraction c, StructuredResult call, CostCalculator.Cost cost,
                            int groundingRejections, List<BrandRow> brands) {
        int n = db.sql("""
                UPDATE answer_extraction
                SET status = 'done', status_reason = NULL, locked_until = NULL, last_error = NULL,
                    model = :model, model_version = :modelVersion,
                    input_tokens = input_tokens + :in, output_tokens = output_tokens + :out,
                    cost_chf = cost_chf + :cost, price_version = :priceVersion, latency_ms = :latency,
                    grounding_rejections = :rejections, finished_at = now()
                WHERE id = :id AND status = 'running' AND attempts = :attempt
                """)
                .param("model", call.requestedModel()).param("modelVersion", call.model())
                .param("in", (int) call.inputTokens()).param("out", (int) call.outputTokens())
                .param("cost", cost.chf()).param("priceVersion", cost.priceVersion())
                .param("latency", (int) call.latency().toMillis()).param("rejections", groundingRejections)
                .param("id", c.id()).param("attempt", c.attempt())
                .update();
        if (n != 1) {
            return false;
        }
        for (BrandRow b : brands) {
            db.sql("""
                    INSERT INTO answer_brand (extraction_id, ordinal, name, normalized, domain, sentiment,
                                              recommended, evidence, position)
                    VALUES (:id, :ordinal, :name, :normalized, :domain, :sentiment, :recommended, :evidence, :position)
                    """)
                    .param("id", c.id()).param("ordinal", b.ordinal()).param("name", b.name())
                    .param("normalized", b.normalized()).param("domain", b.domain())
                    .param("sentiment", b.sentiment()).param("recommended", b.recommended())
                    .param("evidence", b.evidence()).param("position", b.position())
                    .update();
        }
        return true;
    }

    /** Back to the queue after a retryable failure; a billed call's usage and cost are still added. */
    public boolean markRetry(ClaimedExtraction c, String error, Duration backoff, StructuredResult call,
                             CostCalculator.Cost cost) {
        return db.sql("""
                UPDATE answer_extraction
                SET status = 'queued', locked_until = NULL, last_error = :error,
                    next_attempt_at = now() + make_interval(secs => :delay),
                    input_tokens = input_tokens + :in, output_tokens = output_tokens + :out,
                    cost_chf = cost_chf + :cost, price_version = coalesce(:priceVersion, price_version)
                WHERE id = :id AND status = 'running' AND attempts = :attempt
                """)
                .param("error", truncate(error)).param("delay", (double) backoff.toSeconds())
                .param("in", call == null ? 0 : (int) call.inputTokens())
                .param("out", call == null ? 0 : (int) call.outputTokens())
                .param("cost", cost == null ? BigDecimal.ZERO : cost.chf())
                .param("priceVersion", cost == null ? null : cost.priceVersion())
                .param("id", c.id()).param("attempt", c.attempt())
                .update() == 1;
    }

    /** Give up: terminal output (refusal, invalid output) or attempts exhausted. */
    public boolean markFailed(ClaimedExtraction c, String reason, String error, StructuredResult call,
                              CostCalculator.Cost cost) {
        return db.sql("""
                UPDATE answer_extraction
                SET status = 'failed', status_reason = :reason, locked_until = NULL, last_error = :error,
                    model = coalesce(:model, model), model_version = coalesce(:modelVersion, model_version),
                    input_tokens = input_tokens + :in, output_tokens = output_tokens + :out,
                    cost_chf = cost_chf + :cost, price_version = coalesce(:priceVersion, price_version),
                    finished_at = now()
                WHERE id = :id AND status = 'running' AND attempts = :attempt
                """)
                .param("reason", reason).param("error", truncate(error))
                .param("model", call == null ? null : call.requestedModel())
                .param("modelVersion", call == null ? null : call.model())
                .param("in", call == null ? 0 : (int) call.inputTokens())
                .param("out", call == null ? 0 : (int) call.outputTokens())
                .param("cost", cost == null ? BigDecimal.ZERO : cost.chf())
                .param("priceVersion", cost == null ? null : cost.priceVersion())
                .param("id", c.id()).param("attempt", c.attempt())
                .update() == 1;
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
