--liquibase formatted sql

-- ============================================================
-- Audit log: immutable record of all mutation operations
-- ============================================================

CREATE TABLE IF NOT EXISTS doc_generator.audit_log (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         TEXT,
    action          TEXT            NOT NULL,
    resource        TEXT            NOT NULL,
    http_method     TEXT,
    request_body    TEXT,
    ip_address      TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_user_id
    ON doc_generator.audit_log (user_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_action
    ON doc_generator.audit_log (action);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at
    ON doc_generator.audit_log USING brin (created_at);

CREATE INDEX IF NOT EXISTS idx_audit_log_resource
    ON doc_generator.audit_log USING gin (resource gin_trgm_ops);
