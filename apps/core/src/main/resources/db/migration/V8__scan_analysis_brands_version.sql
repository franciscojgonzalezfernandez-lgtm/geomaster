-- V8: scan analysis bookkeeping (spec: specs/extraction-analysis T6; REQ-15, REQ-18).
-- project.brands_version goes up on every brand or alias edit; scan.brands_version is the value
-- its mentions were resolved with. A scan whose value is behind its project's is re-resolved by
-- the ReResolveJob without calling the model again (REQ-15).

ALTER TABLE project ADD COLUMN brands_version integer NOT NULL DEFAULT 0 CHECK (brands_version >= 0);
ALTER TABLE scan ADD COLUMN brands_version integer CHECK (brands_version >= 0);

-- Finished scans still waiting for analysis (the analyser sweep).
CREATE INDEX idx_scan_unanalysed ON scan (finished_at)
    WHERE analysed_at IS NULL AND finished_at IS NOT NULL;
-- Analysed scans of a project (the re-resolve sweep).
CREATE INDEX idx_scan_analysed_project ON scan (project_id) WHERE analysed_at IS NOT NULL;
