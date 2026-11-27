-- extraction-analysis T5: find the scan units (and their scans) that share an answer, for the
-- extraction span's session id now and for per-scan resolution in T6.
CREATE INDEX idx_scan_unit_llm_call ON scan_unit (llm_call_id) WHERE llm_call_id IS NOT NULL;
