-- V6: extraction & analysis (spec: specs/extraction-analysis T1; ADR-0005, ADR-0007, ADR-0013).
-- Brands (hand-entered until the onboarding agent exists), the shared per-answer extraction and
-- the per-tenant resolved mentions and citations.
-- answer_extraction / answer_brand hang off the shared llm_call cache like llm_call_citation: no
-- account id, no tenant data (REQ-6). brand, brand_alias, scan_mention and scan_citation are
-- tenant-owned and tied to their parents AND account with composite foreign keys (as in V3).
--
-- brand_alias.normalized is the matching key (REQ-12). The contract the Java BrandNormalizer
-- (T3) must keep: accents stripped, lower-case, legal suffixes dropped, every non-alphanumeric
-- character removed (so "Ride Flumserberg" and "rideflumserberg.ch" share the key
-- "rideflumserberg"); for domains, scheme, www. and the last label (TLD) are dropped first.
-- A brand's display name is not an alias row; the resolver adds it to the brand's key set.

-- Composite targets for tenant-safe foreign keys. ------------------------------
ALTER TABLE scan_unit ADD CONSTRAINT scan_unit_id_account_uq UNIQUE (id, account_id);

-- Brands (REQ-1..REQ-3). Soft deletes keep past mentions pointing at their brand. -----
CREATE TABLE brand (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id  uuid        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    project_id  uuid        NOT NULL,
    kind        text        NOT NULL CHECK (kind IN ('own', 'competitor')),
    name        text        NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    UNIQUE (id, account_id),
    UNIQUE (id, project_id, account_id),
    FOREIGN KEY (project_id, account_id) REFERENCES project (id, account_id) ON DELETE CASCADE,
    CHECK (kind <> 'own' OR deleted_at IS NULL)          -- the own brand is renamed, never deleted
);
CREATE UNIQUE INDEX uq_brand_one_own_per_project ON brand (project_id)
    WHERE kind = 'own' AND deleted_at IS NULL;
CREATE INDEX idx_brand_project_active ON brand (project_id) WHERE deleted_at IS NULL;

CREATE TABLE brand_alias (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id  uuid        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    project_id  uuid        NOT NULL,
    brand_id    uuid        NOT NULL,
    kind        text        NOT NULL CHECK (kind IN ('name', 'domain')),
    value       text        NOT NULL CHECK (length(btrim(value)) BETWEEN 1 AND 253),
    normalized  text        NOT NULL CHECK (normalized ~ '^[a-z0-9]+$'),
    created_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz,
    FOREIGN KEY (brand_id, project_id, account_id)
        REFERENCES brand (id, project_id, account_id) ON DELETE CASCADE
);
-- One brand per matching key in a project (REQ-2): resolution is never ambiguous.
CREATE UNIQUE INDEX uq_brand_alias_project_normalized ON brand_alias (project_id, normalized)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_brand_alias_brand ON brand_alias (brand_id);

-- Extraction = shared work queue, one row per llm_call x extractor version (REQ-4, REQ-16). --
CREATE TABLE answer_extraction (
    id                    uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    llm_call_id           uuid          NOT NULL REFERENCES llm_call(id) ON DELETE CASCADE,
    extractor_version     text          NOT NULL CHECK (extractor_version ~ '^ext-[0-9]+$'),
    status                text          NOT NULL DEFAULT 'queued'
                              CHECK (status IN ('queued', 'running', 'done', 'failed')),
    status_reason         text,                       -- e.g. refused, invalid_output, max_attempts
    attempts              smallint      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at       timestamptz   NOT NULL DEFAULT now(),
    locked_until          timestamptz,                -- lease; an expired lease is reclaimable
    last_error            text,
    model                 text,
    model_version         text,
    input_tokens          integer       NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    output_tokens         integer       NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    cost_chf              numeric(12,6) NOT NULL DEFAULT 0 CHECK (cost_chf >= 0),
    price_version         text,
    latency_ms            integer       CHECK (latency_ms >= 0),
    grounding_rejections  integer       NOT NULL DEFAULT 0 CHECK (grounding_rejections >= 0),
    created_at            timestamptz   NOT NULL DEFAULT now(),
    finished_at           timestamptz,
    UNIQUE (llm_call_id, extractor_version),
    CHECK (status <> 'done' OR (model IS NOT NULL AND price_version IS NOT NULL AND finished_at IS NOT NULL)),
    CHECK (status <> 'failed' OR (status_reason IS NOT NULL AND finished_at IS NOT NULL))
);
CREATE INDEX idx_answer_extraction_claim_queued ON answer_extraction (next_attempt_at) WHERE status = 'queued';
CREATE INDEX idx_answer_extraction_claim_expired ON answer_extraction (locked_until) WHERE status = 'running';

-- Grounded brands of one extraction (REQ-5, REQ-7, REQ-8). Written with the done row. ------
CREATE TABLE answer_brand (
    extraction_id  uuid     NOT NULL REFERENCES answer_extraction(id) ON DELETE CASCADE,
    ordinal        integer  NOT NULL CHECK (ordinal >= 1),       -- model output order
    name           text     NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 200),
    normalized     text     NOT NULL CHECK (normalized ~ '^[a-z0-9]+$'),
    domain         text,
    sentiment      text     NOT NULL CHECK (sentiment IN ('positive', 'neutral', 'negative')),
    recommended    boolean  NOT NULL,
    evidence       text     NOT NULL CHECK (char_length(evidence) <= 200),
    position       integer  NOT NULL CHECK (position >= 1),      -- first occurrence in the text
    PRIMARY KEY (extraction_id, ordinal)
);

-- Resolved mentions per scan unit (REQ-12, REQ-13). Rewritten per scan on re-resolution. ---
CREATE TABLE scan_mention (
    account_id        uuid     NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    scan_id           uuid     NOT NULL,
    scan_unit_id      uuid     NOT NULL,
    extraction_id     uuid     NOT NULL,
    ordinal           integer  NOT NULL,               -- answer_brand row kept for the mention
    brand_id          uuid,                            -- NULL = untracked
    extracted_name    text     NOT NULL,
    position          integer  NOT NULL CHECK (position >= 1),
    sentiment         text     NOT NULL CHECK (sentiment IN ('positive', 'neutral', 'negative')),
    recommended       boolean  NOT NULL,
    resolver_version  text     NOT NULL CHECK (resolver_version ~ '^res-[0-9]+$'),
    PRIMARY KEY (scan_unit_id, ordinal),
    FOREIGN KEY (scan_id, account_id) REFERENCES scan (id, account_id) ON DELETE CASCADE,
    FOREIGN KEY (scan_unit_id, account_id) REFERENCES scan_unit (id, account_id) ON DELETE CASCADE,
    FOREIGN KEY (extraction_id, ordinal) REFERENCES answer_brand (extraction_id, ordinal) ON DELETE CASCADE,
    FOREIGN KEY (brand_id, account_id) REFERENCES brand (id, account_id)
);
-- Same brand twice in one answer counts once (REQ-13).
CREATE UNIQUE INDEX uq_scan_mention_unit_brand ON scan_mention (scan_unit_id, brand_id)
    WHERE brand_id IS NOT NULL;
CREATE INDEX idx_scan_mention_scan ON scan_mention (scan_id);
CREATE INDEX idx_scan_mention_brand ON scan_mention (brand_id) WHERE brand_id IS NOT NULL;

-- Cited URLs per scan unit with their class (REQ-9, REQ-14). -----------------------------
CREATE TABLE scan_citation (
    account_id        uuid  NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    scan_id           uuid  NOT NULL,
    scan_unit_id      uuid  NOT NULL,
    url               text  NOT NULL,
    domain            text  NOT NULL,
    source            text  NOT NULL CHECK (source IN ('provider', 'text')),
    class             text  NOT NULL CHECK (class IN ('own', 'competitor', 'third_party')),
    brand_id          uuid,
    resolver_version  text  NOT NULL CHECK (resolver_version ~ '^res-[0-9]+$'),
    PRIMARY KEY (scan_unit_id, url),
    FOREIGN KEY (scan_id, account_id) REFERENCES scan (id, account_id) ON DELETE CASCADE,
    FOREIGN KEY (scan_unit_id, account_id) REFERENCES scan_unit (id, account_id) ON DELETE CASCADE,
    FOREIGN KEY (brand_id, account_id) REFERENCES brand (id, account_id),
    CHECK ((class = 'third_party') = (brand_id IS NULL))
);
CREATE INDEX idx_scan_citation_scan ON scan_citation (scan_id);

-- Scan analysis totals (REQ-16, REQ-18). -------------------------------------------------
ALTER TABLE scan
    ADD COLUMN analysed_at           timestamptz,
    ADD COLUMN extraction_cost_chf   numeric(12,6) NOT NULL DEFAULT 0 CHECK (extraction_cost_chf >= 0),
    ADD COLUMN answers_extracted     integer       NOT NULL DEFAULT 0 CHECK (answers_extracted >= 0),
    ADD COLUMN extractions_failed    integer       NOT NULL DEFAULT 0 CHECK (extractions_failed >= 0),
    ADD COLUMN grounding_rejections  integer       NOT NULL DEFAULT 0 CHECK (grounding_rejections >= 0);

-- Backfill: every existing project gets its own brand with its domain as alias (REQ-1). ----
-- Display name from the first label: "ride-flumserberg.ch" -> "Ride Flumserberg".
WITH own AS (
    INSERT INTO brand (account_id, project_id, kind, name)
    SELECT p.account_id, p.id, 'own', initcap(replace(split_part(p.domain, '.', 1), '-', ' '))
    FROM project p
    WHERE NOT EXISTS (SELECT 1 FROM brand b WHERE b.project_id = p.id AND b.kind = 'own')
    RETURNING id, account_id, project_id
)
INSERT INTO brand_alias (account_id, project_id, brand_id, kind, value, normalized)
SELECT own.account_id, own.project_id, own.id, 'domain', p.domain,
       regexp_replace(regexp_replace(p.domain, '\.[^.]+$', ''), '[^a-z0-9]', '', 'g')
FROM own JOIN project p ON p.id = own.project_id;
