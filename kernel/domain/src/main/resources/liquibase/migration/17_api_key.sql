--liquibase formatted sql

-- ============================================================
-- API Key management for authentication
-- ============================================================

CREATE TABLE IF NOT EXISTS doc_generator.api_key (
    id              BIGSERIAL       PRIMARY KEY,
    name            TEXT            NOT NULL,
    key_hash        TEXT            NOT NULL UNIQUE,
    scopes          TEXT[]          NOT NULL DEFAULT '{}',
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_api_key_key_hash
    ON doc_generator.api_key (key_hash);

CREATE INDEX IF NOT EXISTS idx_api_key_active
    ON doc_generator.api_key (active) WHERE active = true;
