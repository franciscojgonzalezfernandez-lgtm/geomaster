-- V2: accounts & projects (spec: specs/accounts-projects; ADR-0005, ADR-0017).
-- Flyway owns the schema (ADR-0005); apps/web never migrates. Every tenant-owned
-- table carries account_id. Better Auth tables are created here with pinned,
-- snake_case names (mapped in Better Auth config), and Better Auth runs with
-- advanced.database.generateId=false so the DB supplies uuid ids. Its "account"
-- model (an OAuth provider link) is named auth_identity to avoid colliding with
-- the business tenant table `account`.

CREATE EXTENSION IF NOT EXISTS citext;

-- Identity: Better Auth "user" model; app_user doubles as it. ------------------
CREATE TABLE app_user (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email             citext      NOT NULL UNIQUE,
    email_verified    boolean     NOT NULL DEFAULT false,
    name              text,
    image             text,
    interface_locale  text        NOT NULL DEFAULT 'en'
                          CHECK (interface_locale IN ('en', 'de', 'fr', 'es')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

-- Tenant (business account) + membership. -------------------------------------
CREATE TABLE account (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text        NOT NULL,
    plan_id     text        NOT NULL DEFAULT 'free',  -- matches packages/plans/plans.json id
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE account_member (
    account_id  uuid        NOT NULL REFERENCES account(id)  ON DELETE CASCADE,
    user_id     uuid        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role        text        NOT NULL DEFAULT 'member' CHECK (role IN ('owner', 'member')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, user_id)
);
CREATE INDEX idx_account_member_user ON account_member (user_id);

-- Projects. -------------------------------------------------------------------
CREATE TABLE project (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id  uuid        NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    domain      text        NOT NULL,
    country     char(2)     NOT NULL,                 -- ISO 3166-1 alpha-2
    region      text,
    status      text        NOT NULL DEFAULT 'active'
                    CHECK (status IN ('active', 'paused', 'archived')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (account_id, domain)                        -- idempotent create (REQ-10)
);
CREATE INDEX idx_project_account ON project (account_id);

CREATE TABLE project_language (
    project_id  uuid NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    locale      text NOT NULL CHECK (locale IN ('en', 'de', 'fr', 'es')),
    PRIMARY KEY (project_id, locale)
);

-- Better Auth: session. -------------------------------------------------------
CREATE TABLE auth_session (
    id                      uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 uuid        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token                   text        NOT NULL UNIQUE,
    expires_at              timestamptz NOT NULL,
    ip_address              text,
    user_agent              text,
    active_account_id       uuid        REFERENCES account(id) ON DELETE SET NULL,  -- tenant for the token's `acct` claim
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_session_user ON auth_session (user_id);

-- Better Auth: OAuth provider link (its "account" model, renamed). ------------
CREATE TABLE auth_identity (
    id                        uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                   uuid        NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider_id               text        NOT NULL,   -- e.g. 'google'
    provider_account_id       text        NOT NULL,   -- Better Auth field accountId
    access_token              text,
    refresh_token             text,
    id_token                  text,
    access_token_expires_at   timestamptz,
    refresh_token_expires_at  timestamptz,
    scope                     text,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider_id, provider_account_id)
);
CREATE INDEX idx_auth_identity_user ON auth_identity (user_id);

-- Better Auth: magic-link / email verification. -------------------------------
CREATE TABLE auth_verification (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    identifier  text        NOT NULL,
    value       text        NOT NULL,
    expires_at  timestamptz NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_verification_identifier ON auth_verification (identifier);
