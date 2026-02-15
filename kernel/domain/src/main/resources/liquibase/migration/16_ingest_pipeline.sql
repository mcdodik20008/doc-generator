--liquibase formatted sql

-- ============================================================
-- Ingest Pipeline: run / step / event tracking
-- ============================================================

-- 1. ingest_run — каждый запуск индексации
CREATE TABLE IF NOT EXISTS doc_generator.ingest_run (
    id              BIGSERIAL    PRIMARY KEY,
    application_id  BIGINT       NOT NULL
        REFERENCES doc_generator.application(id) ON DELETE CASCADE,
    status          TEXT         NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED')),
    triggered_by    TEXT,
    branch          TEXT,
    commit_sha      TEXT,
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ingest_run_application_id
    ON doc_generator.ingest_run (application_id);

CREATE INDEX IF NOT EXISTS idx_ingest_run_created_at
    ON doc_generator.ingest_run USING brin (created_at);

-- 2. ingest_step — шаг внутри запуска
CREATE TABLE IF NOT EXISTS doc_generator.ingest_step (
    id              BIGSERIAL    PRIMARY KEY,
    run_id          BIGINT       NOT NULL
        REFERENCES doc_generator.ingest_run(id) ON DELETE CASCADE,
    step_type       TEXT         NOT NULL
        CHECK (step_type IN ('CHECKOUT','RESOLVE_CLASSPATH','BUILD_LIBRARY','BUILD_GRAPH','LINK')),
    status          TEXT         NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','SKIPPED')),
    items_processed INT,
    items_total     INT,
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ingest_step_run_id
    ON doc_generator.ingest_step (run_id);

-- 3. ingest_event — лог-событие
CREATE TABLE IF NOT EXISTS doc_generator.ingest_event (
    id              BIGSERIAL    PRIMARY KEY,
    run_id          BIGINT       NOT NULL
        REFERENCES doc_generator.ingest_run(id) ON DELETE CASCADE,
    step_type       TEXT,
    level           TEXT         NOT NULL DEFAULT 'INFO'
        CHECK (level IN ('INFO','WARN','ERROR')),
    message         TEXT         NOT NULL,
    context         JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ingest_event_run_id
    ON doc_generator.ingest_event (run_id);

CREATE INDEX IF NOT EXISTS idx_ingest_event_created_at
    ON doc_generator.ingest_event USING brin (created_at);
