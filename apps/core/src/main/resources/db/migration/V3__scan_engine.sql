-- V3: scan engine (spec: specs/scan-engine; ADR-0004, ADR-0005, ADR-0007).
-- Prompts (hand-entered until the onboarding agent exists), scans, the scan_unit work queue
-- (SELECT ... FOR UPDATE SKIP LOCKED) and the provider calls with citations and real cost.
-- Tenant-owned tables carry account_id with ON DELETE CASCADE from account (REQ-18 of
-- accounts-projects). Child rows are tied to their parent AND its account with composite foreign
-- keys, so a row can never point at another tenant's project, prompt or scan.
-- llm_call / llm_call_citation are NOT tenant-owned: they are the response cache shared across
-- accounts (FR-SCN-04). They store a hash of the prompt text, never the text or any account id.

-- Composite targets for tenant-safe foreign keys. ------------------------------
ALTER TABLE project ADD CONSTRAINT project_id_account_uq UNIQUE (id, account_id);

-- Prompts. ---------------------------------------------------------------------
CREATE TABLE prompt (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       uuid        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    project_id       uuid        NOT NULL,
    locale           text        NOT NULL CHECK (locale IN ('en', 'de', 'fr', 'es')),
    topic            text        NOT NULL CHECK (topic IN
                         ('reviews', 'pricing', 'use_cases', 'alternatives', 'how_to', 'comparison')),
    current_version  integer     NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    deleted_at       timestamptz,                       -- soft delete: never asked again (REQ-2)
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, account_id),
    FOREIGN KEY (project_id, account_id) REFERENCES project (id, account_id) ON DELETE CASCADE
);
CREATE INDEX idx_prompt_project_active ON prompt (project_id) WHERE deleted_at IS NULL;

-- Every edit is a new version; answers keep the exact text they were asked with (NFR-13).
CREATE TABLE prompt_version (
    prompt_id   uuid        NOT NULL,
    version     integer     NOT NULL CHECK (version >= 1),
    account_id  uuid        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    text        text        NOT NULL CHECK (length(btrim(text)) BETWEEN 1 AND 2000),
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (prompt_id, version),
    UNIQUE (prompt_id, version, account_id),
    FOREIGN KEY (prompt_id, account_id) REFERENCES prompt (id, account_id) ON DELETE CASCADE
);

-- Scans. -----------------------------------------------------------------------
CREATE TABLE scan (
    id             uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id     uuid          NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    project_id     uuid          NOT NULL,
    status         text          NOT NULL DEFAULT 'queued'
                       CHECK (status IN ('queued', 'running', 'completed', 'partial', 'failed')),
    status_reason  text,
    trigger        text          NOT NULL CHECK (trigger IN ('manual', 'schedule')),
    samples        smallint      NOT NULL CHECK (samples BETWEEN 1 AND 5),
    units_total    integer       NOT NULL DEFAULT 0 CHECK (units_total >= 0),
    units_done     integer       NOT NULL DEFAULT 0 CHECK (units_done >= 0),
    units_failed   integer       NOT NULL DEFAULT 0 CHECK (units_failed >= 0),
    cost_chf       numeric(12,6) NOT NULL DEFAULT 0 CHECK (cost_chf >= 0),
    versions       jsonb         NOT NULL DEFAULT '{}'::jsonb,  -- models, prices, formula, extractor
    created_at     timestamptz   NOT NULL DEFAULT now(),
    started_at     timestamptz,
    finished_at    timestamptz,
    UNIQUE (id, account_id),
    FOREIGN KEY (project_id, account_id) REFERENCES project (id, account_id) ON DELETE CASCADE
);
CREATE INDEX idx_scan_project_created ON scan (project_id, created_at DESC);
-- At most one queued/running scan per project (REQ-6).
CREATE UNIQUE INDEX uq_scan_one_active_per_project ON scan (project_id)
    WHERE status IN ('queued', 'running');

-- Provider calls = the shared response cache (FR-SCN-04, FR-SCN-06). ---------------
CREATE TABLE llm_call (
    id                       uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- sha256(prompt text) + engine + model + locale + country + region + UTC day
    cache_key                text          NOT NULL UNIQUE,
    engine                   text          NOT NULL CHECK (engine IN ('openai', 'gemini', 'claude')),
    model                    text          NOT NULL,
    model_version            text,
    locale                   text          NOT NULL CHECK (locale IN ('en', 'de', 'fr', 'es')),
    country                  char(2),
    region                   text,
    prompt_sha256            char(64)      NOT NULL,
    answer_text              text          NOT NULL,
    search_queries           jsonb         NOT NULL DEFAULT '[]'::jsonb,
    search_count             integer       NOT NULL DEFAULT 0 CHECK (search_count >= 0),
    input_tokens             integer       NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    output_tokens            integer       NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    cost_chf                 numeric(12,6) NOT NULL CHECK (cost_chf >= 0),
    price_version            text          NOT NULL,
    search_suggestions_html  text,                     -- Gemini grounding search entry point (FR-SCN-07)
    latency_ms               integer       NOT NULL CHECK (latency_ms >= 0),
    created_at               timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX idx_llm_call_engine_created ON llm_call (engine, created_at);

CREATE TABLE llm_call_citation (
    llm_call_id  uuid    NOT NULL REFERENCES llm_call(id) ON DELETE CASCADE,
    position     integer NOT NULL CHECK (position >= 1),
    url          text    NOT NULL,
    title        text,
    domain       text    NOT NULL,
    PRIMARY KEY (llm_call_id, position)
);
CREATE INDEX idx_llm_call_citation_domain ON llm_call_citation (domain);

-- Work queue: one row per prompt x engine x language x sample (ADR-0004). -----------
CREATE TABLE scan_unit (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       uuid          NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    scan_id          uuid          NOT NULL,
    prompt_id        uuid          NOT NULL,
    prompt_version   integer       NOT NULL,   -- REQ-12: the exact prompt text asked (via prompt_version)
    engine           text          NOT NULL CHECK (engine IN ('openai', 'gemini', 'claude')),
    locale           text          NOT NULL CHECK (locale IN ('en', 'de', 'fr', 'es')),
    sample           smallint      NOT NULL CHECK (sample >= 1),
    status           text          NOT NULL DEFAULT 'pending'
                         CHECK (status IN ('pending', 'running', 'done', 'failed')),
    attempts         smallint      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at  timestamptz   NOT NULL DEFAULT now(),
    locked_until     timestamptz,              -- lease; an expired lease is reclaimable
    last_error       text,
    llm_call_id      uuid          REFERENCES llm_call(id),
    cache_hit        boolean       NOT NULL DEFAULT false,
    cost_chf         numeric(12,6) NOT NULL DEFAULT 0 CHECK (cost_chf >= 0),  -- 0 on a cache hit
    created_at       timestamptz   NOT NULL DEFAULT now(),
    finished_at      timestamptz,
    -- Idempotency key (FR-SCN-05): a retried scan can never create or repeat the same unit.
    UNIQUE (scan_id, prompt_id, engine, locale, sample),
    FOREIGN KEY (scan_id, account_id) REFERENCES scan (id, account_id) ON DELETE CASCADE,
    FOREIGN KEY (prompt_id, prompt_version, account_id)
        REFERENCES prompt_version (prompt_id, version, account_id) ON DELETE CASCADE,
    CHECK (status <> 'done' OR llm_call_id IS NOT NULL)
);
CREATE INDEX idx_scan_unit_claim_pending ON scan_unit (next_attempt_at) WHERE status = 'pending';
CREATE INDEX idx_scan_unit_claim_expired ON scan_unit (locked_until) WHERE status = 'running';
CREATE INDEX idx_scan_unit_scan_status ON scan_unit (scan_id, status);
