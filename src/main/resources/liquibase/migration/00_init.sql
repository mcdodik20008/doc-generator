--liquibase formatted sql

-- ======================================================================
-- 0. Preconditions (PostgreSQL only)
-- ======================================================================
--changeset arch:000_preconditions context:prod,dev runOnChange:true
--comment: Ensure running on PostgreSQL and required extensions availability
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT 1;
--rollback SELECT 1;
--endChangeset


-- ======================================================================
-- 1. Extensions & schema
-- ======================================================================
--changeset arch:001_extensions context:prod,dev runOnChange:true
--comment: Create required extensions (pg_trgm, vector, unaccent) and schema
CREATE SCHEMA IF NOT EXISTS doc_generator;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS unaccent;
--rollback DO $$ BEGIN /* usually we do not drop shared extensions */ END $$;
--endChangeset


-- ======================================================================
-- 1.1 RU+EN FTS helpers
-- ======================================================================
--changeset arch:005_ru_en_fts_helpers context:prod,dev splitStatements:false endDelimiter:;
--comment: Helpers for multilingual FTS (RU+EN)

CREATE OR REPLACE FUNCTION doc_generator.make_ru_en_tsv(txt TEXT)
    RETURNS tsvector
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
AS
'
    SELECT coalesce(to_tsvector(''russian'', unaccent(txt)), ''''::tsvector)
               || coalesce(to_tsvector(''english'', unaccent(txt)), ''''::tsvector);
';

CREATE OR REPLACE FUNCTION doc_generator.make_ru_en_tsquery(q TEXT)
    RETURNS tsquery
    LANGUAGE sql
    STABLE
    PARALLEL SAFE
AS
'
    SELECT coalesce(websearch_to_tsquery(''russian'', unaccent(q)), ''''::tsquery)
               || coalesce(websearch_to_tsquery(''english'', unaccent(q)), ''''::tsquery);
';

--rollback DROP FUNCTION IF EXISTS doc_generator.make_ru_en_tsquery(TEXT);
--rollback DROP FUNCTION IF EXISTS doc_generator.make_ru_en_tsv(TEXT);
--endChangeset


-- ======================================================================
-- 2. application
-- ======================================================================
--changeset arch:010_application context:prod,dev
--comment: Applications registry (multi-app isolation)
CREATE TABLE IF NOT EXISTS doc_generator.application
(
    id             BIGSERIAL PRIMARY KEY,
    key            TEXT        NOT NULL UNIQUE,
    name           TEXT        NOT NULL,
    repo_url       TEXT,
    default_branch TEXT        NOT NULL DEFAULT 'main',
    metadata       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE doc_generator.application IS 'microservices';
COMMENT ON COLUMN doc_generator.application.key IS 'Stable key used in API header X-App-Key.';

CREATE OR REPLACE FUNCTION doc_generator.trg_touch_updated_at() RETURNS TRIGGER AS
'
    BEGIN
        NEW.updated_at := now(); RETURN NEW;
    END;
' LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_application_touch ON doc_generator.application;
CREATE TRIGGER trg_application_touch
    BEFORE UPDATE
    ON doc_generator.application
    FOR EACH ROW
EXECUTE FUNCTION doc_generator.trg_touch_updated_at();

--rollback DROP TRIGGER IF EXISTS trg_application_touch ON doc_generator.application;
--rollback DROP FUNCTION IF EXISTS doc_generator.trg_touch_updated_at();
--rollback DROP TABLE IF EXISTS doc_generator.application;
--endChangeset


-- ======================================================================
-- 3. node
-- ======================================================================
--changeset arch:020_node context:prod,dev
--comment: Code/Artifact nodes
CREATE TABLE IF NOT EXISTS doc_generator.node
(
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT      NOT NULL REFERENCES doc_generator.application (id) ON DELETE CASCADE,
    fqn            TEXT        NOT NULL, -- fully-qualified name
    kind           TEXT        NOT NULL,
    lang           TEXT        NOT NULL, -- kotlin|java|sql|yaml|md|other
    file_path      TEXT,
    line_start     INT,
    line_end       INT,
    meta           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_node_app_fqn UNIQUE (application_id, fqn),
    CONSTRAINT ck_node_kind CHECK (
        kind IN (
                 'MODULE', 'PACKAGE', 'CLASS', 'INTERFACE', 'ENUM', 'RECORD', 'FIELD',
                 'METHOD', 'ENDPOINT', 'TOPIC', 'DBTABLE', 'MIGRATION', 'CONFIG', 'JOB'
            )),
    CONSTRAINT ck_node_lang CHECK (lang IN ('kotlin', 'java', 'sql', 'yaml', 'md', 'other'))
);

COMMENT ON TABLE doc_generator.node IS 'Graph nodes representing code and system artifacts.';
COMMENT ON COLUMN doc_generator.node.fqn IS 'Fully-qualified name (unique within application).';

CREATE INDEX IF NOT EXISTS ix_node_app ON doc_generator.node (application_id);
CREATE INDEX IF NOT EXISTS ix_node_kind ON doc_generator.node (kind);
CREATE INDEX IF NOT EXISTS ix_node_path_trgm ON doc_generator.node USING gin (file_path gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_node_fqn_trgm ON doc_generator.node USING gin (fqn gin_trgm_ops);

DROP TRIGGER IF EXISTS trg_node_touch ON doc_generator.node;
CREATE TRIGGER trg_node_touch
    BEFORE UPDATE
    ON doc_generator.node
    FOR EACH ROW
EXECUTE FUNCTION doc_generator.trg_touch_updated_at();

--rollback DROP TRIGGER IF EXISTS trg_node_touch ON doc_generator.node;
--rollback DROP INDEX IF EXISTS doc_generator.ix_node_fqn_trgm;
--rollback DROP INDEX IF EXISTS doc_generator.ix_node_path_trgm;
--rollback DROP INDEX IF EXISTS doc_generator.ix_node_kind;
--rollback DROP INDEX IF EXISTS doc_generator.ix_node_app;
--rollback DROP TABLE IF EXISTS doc_generator.node;
--endChangeset


-- ======================================================================
-- 4. edge
-- ======================================================================
--changeset arch:030_edge context:prod,dev
--comment: Directed edges between nodes
CREATE TABLE IF NOT EXISTS doc_generator.edge
(
    src_id     BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    dst_id     BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    kind       TEXT        NOT NULL,
    evidence   JSONB       NOT NULL DEFAULT '{}'::jsonb, -- snippets/spans/sql/plan/etc
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (src_id, dst_id, kind),
    CONSTRAINT ck_edge_kind CHECK (kind IN (
                                            'CALLS', 'READS', 'WRITES', 'QUERIES', 'PUBLISHES', 'CONSUMES',
                                            'THROWS', 'IMPLEMENTS', 'OVERRIDES', 'LOCKS', 'OPENTELEMETRY',
                                            'USES_FEATURE', 'DEPENDS_ON'
        ))
);

COMMENT ON TABLE doc_generator.edge IS 'Directed dependency edges in the code/system graph.';

CREATE INDEX IF NOT EXISTS ix_edge_src ON doc_generator.edge (src_id);
CREATE INDEX IF NOT EXISTS ix_edge_dst ON doc_generator.edge (dst_id);
CREATE INDEX IF NOT EXISTS ix_edge_kind ON doc_generator.edge (kind);
CREATE INDEX IF NOT EXISTS ix_edge_src_kind ON doc_generator.edge (src_id, kind);
CREATE INDEX IF NOT EXISTS ix_edge_dst_kind ON doc_generator.edge (dst_id, kind);

--rollback DROP INDEX IF EXISTS doc_generator.ix_edge_dst_kind;
--rollback DROP INDEX IF EXISTS doc_generator.ix_edge_src_kind;
--rollback DROP INDEX IF EXISTS doc_generator.ix_edge_kind;
--rollback DROP INDEX IF EXISTS doc_generator.ix_edge_dst;
--rollback DROP INDEX IF EXISTS doc_generator.ix_edge_src;
--rollback DROP TABLE IF EXISTS doc_generator.edge;
--endChangeset


-- ======================================================================
-- 5. chunk (text/code/doc fragments with FTS + vector)
-- ======================================================================
--changeset arch:040_chunk context:prod,dev
--comment: Chunks of text/code bound to nodes to support RAG (BM25 + vector)
CREATE TABLE IF NOT EXISTS doc_generator.chunk
(
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT      NOT NULL REFERENCES doc_generator.application (id) ON DELETE CASCADE,
    node_id        BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    source         TEXT        NOT NULL, -- code|doc|sql|log
    content        TEXT        NOT NULL,

    -- RU+EN tsvector (GENERATED STORED)
    content_tsv    tsvector GENERATED ALWAYS AS (doc_generator.make_ru_en_tsv(content)) STORED,

    emb            vector(1024),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chunk_source CHECK (source IN ('code', 'doc', 'sql', 'log'))
);

COMMENT ON TABLE doc_generator.chunk IS 'RAG chunks with both BM25/FTS and vector embeddings.';
COMMENT ON COLUMN doc_generator.chunk.emb IS 'Vector embedding (cosine ops).';

CREATE INDEX IF NOT EXISTS ix_chunk_app ON doc_generator.chunk (application_id);
CREATE INDEX IF NOT EXISTS ix_chunk_node ON doc_generator.chunk (node_id);
CREATE INDEX IF NOT EXISTS ix_chunk_fts ON doc_generator.chunk USING gin (content_tsv);

DROP TRIGGER IF EXISTS trg_chunk_touch ON doc_generator.chunk;
CREATE TRIGGER trg_chunk_touch
    BEFORE UPDATE
    ON doc_generator.chunk
    FOR EACH ROW
EXECUTE FUNCTION doc_generator.trg_touch_updated_at();

--rollback DROP TRIGGER IF EXISTS trg_chunk_touch ON doc_generator.chunk;
--rollback DROP INDEX IF EXISTS doc_generator.ix_chunk_fts;
--rollback DROP INDEX IF EXISTS doc_generator.ix_chunk_node;
--rollback DROP INDEX IF EXISTS doc_generator.ix_chunk_app;
--rollback DROP TABLE IF EXISTS doc_generator.chunk;
--endChangeset


-- ======================================================================
-- 5.1 Vector index (ivfflat) — отдельный шаг без транзакции
-- ======================================================================
--changeset arch:041_chunk_ivfflat context:prod,dev runInTransaction:false
--comment: IVFFLAT index for vector search (requires ANALYZE, lists tuned per data size)
DO
'
    BEGIN
        IF NOT EXISTS (SELECT 1
                       FROM pg_indexes
                       WHERE schemaname = ''doc_generator''
                         AND indexname = ''ix_chunk_emb'') THEN
            EXECUTE ''CREATE INDEX ix_chunk_emb ON doc_generator.chunk USING ivfflat (emb vector_cosine_ops) WITH (lists=200);'';
        END IF;
    END
';
--rollback DROP INDEX IF EXISTS doc_generator.ix_chunk_emb;
--endChangeset


-- ======================================================================
-- 6. Helpful views
-- ======================================================================
--changeset arch:050_views context:prod,dev
--comment: Views for cross-app edges and node cards
CREATE OR REPLACE VIEW doc_generator.v_cross_app_edges AS
SELECT e.kind,
       s.id   AS src_id,
       s.fqn  AS src_fqn,
       sa.key AS src_app,
       d.id   AS dst_id,
       d.fqn  AS dst_fqn,
       da.key AS dst_app,
       e.evidence
FROM doc_generator.edge e
         JOIN doc_generator.node s ON s.id = e.src_id
         JOIN doc_generator.node d ON d.id = e.dst_id
         JOIN doc_generator.application sa ON sa.id = s.application_id
         JOIN doc_generator.application da ON da.id = d.application_id
WHERE s.application_id <> d.application_id;

CREATE OR REPLACE VIEW doc_generator.v_node_card AS
SELECT n.id,
       a.key AS app_key,
       n.fqn,
       n.kind,
       n.lang,
       n.file_path,
       n.line_start,
       n.line_end,
       n.meta
FROM doc_generator.node n
         JOIN doc_generator.application a ON a.id = n.application_id;

--rollback DROP VIEW IF EXISTS doc_generator.v_node_card;
--rollback DROP VIEW IF EXISTS doc_generator.v_cross_app_edges;
--endChangeset


-- ======================================================================
-- 7. Seeds (optional)
-- ======================================================================
--changeset arch:060_seed_apps context:dev
--comment: Seed a few sample applications
INSERT INTO doc_generator.application(key, name)
VALUES ('billing', 'Billing Service'),
       ('identity', 'Identity Service'),
       ('catalog', 'Catalog Service')
ON CONFLICT (key) DO NOTHING;
--rollback DELETE FROM doc_generator.application WHERE key IN ('billing','identity','catalog');
--endChangeset
