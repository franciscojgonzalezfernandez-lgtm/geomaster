package com.geomaster.core.extraction;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Ensures one extraction per answer and extractor version (REQ-4). Called inside the transaction
 * that marks a scan unit done, so a done unit always has its extraction queued; a cache hit finds
 * the existing row and adds nothing. Without an extractor (disabled) nothing is queued.
 */
@Component
public class ExtractionEnqueuer {

    private final JdbcClient db;
    private final ObjectProvider<Extractor> extractor;

    public ExtractionEnqueuer(JdbcClient db, ObjectProvider<Extractor> extractor) {
        this.db = db;
        this.extractor = extractor;
    }

    /** @return true when a new extraction was queued */
    public boolean enqueue(UUID llmCallId) {
        Extractor x = extractor.getIfAvailable();
        if (x == null) {
            return false;
        }
        return db.sql("""
                INSERT INTO answer_extraction (llm_call_id, extractor_version)
                VALUES (:call, :version)
                ON CONFLICT (llm_call_id, extractor_version) DO NOTHING
                """)
                .param("call", llmCallId).param("version", x.version().version())
                .update() == 1;
    }
}
