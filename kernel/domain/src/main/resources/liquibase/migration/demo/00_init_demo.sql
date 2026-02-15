--liquibase formatted sql

--changeset arch:001_init context:prod
--comment: Doc Generator — functions, enums, tables, indexes, triggers (DEMO: BYTEA instead of vector)
-- ===== FTS helpers (RU+EN) =====
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

-- ===== Enums =====
CREATE TYPE doc_generator.node_kind AS ENUM (
    'MODULE','PACKAGE','CLASS','INTERFACE','ENUM','RECORD','FIELD',
    'METHOD','ENDPOINT','TOPIC','DBTABLE','MIGRATION','CONFIG','JOB'
    );

CREATE TYPE doc_generator.edge_kind AS ENUM (
    'CALLS','READS','WRITES','QUERIES','PUBLISHES','CONSUMES','THROWS',
    'IMPLEMENTS','OVERRIDES','LOCKS','OPENTELEMETRY','USES_FEATURE','DEPENDS_ON'
    );

CREATE TYPE doc_generator.lang AS ENUM ('kotlin','java','sql','yaml','md','other');

--changeset arch:000_app context:prod
--comment: Applications registry
CREATE TABLE doc_generator.application
(
    id                BIGSERIAL PRIMARY KEY,
    key               TEXT        NOT NULL UNIQUE,
    name              TEXT        NOT NULL,
    description       TEXT,
    repo_url          TEXT,
    repo_provider     TEXT,
    repo_owner        TEXT,
    repo_name         TEXT,
    monorepo_path     TEXT,
    default_branch    TEXT        NOT NULL DEFAULT 'main',
    last_commit_sha   TEXT,
    last_indexed_at   TIMESTAMPTZ,
    last_index_status TEXT,
    last_index_error  TEXT,
    ingest_cursor     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    owners            JSONB       NOT NULL DEFAULT '[]'::jsonb,
    contacts          JSONB       NOT NULL DEFAULT '[]'::jsonb,
    tags              TEXT[]      NOT NULL DEFAULT '{}'::text[],
    languages         TEXT[]      NOT NULL DEFAULT '{}'::text[],
    embedding_model   TEXT,
    embedding_dim     INT         NOT NULL DEFAULT 1024,
    ann_index_params  JSONB       NOT NULL DEFAULT '{"method": "ivfflat", "lists": 100}'::jsonb,
    rag_prefs         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    retention_days    INT,
    pii_policy        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    metadata          JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_app_key_slug CHECK (key ~ '^[a-z0-9][a-z0-9\-]{2,64}$'),
    CONSTRAINT ck_app_embedding_dim CHECK (embedding_dim > 0),
    CONSTRAINT ck_app_last_index_status CHECK (last_index_status IS NULL OR
                                               last_index_status IN ('success', 'failed', 'partial', 'running', 'queued')),
    CONSTRAINT ck_app_repo_provider CHECK (repo_provider IS NULL OR
                                           repo_provider IN ('github', 'gitlab', 'bitbucket', 'gitea', 'other')),
    CONSTRAINT ck_app_owners_arr CHECK (jsonb_typeof(owners) = 'array'),
    CONSTRAINT ck_app_contacts_arr CHECK (jsonb_typeof(contacts) = 'array'),
    CONSTRAINT ck_app_ann_obj CHECK (jsonb_typeof(ann_index_params) = 'object'),
    CONSTRAINT ck_app_rag_obj CHECK (jsonb_typeof(rag_prefs) = 'object'),
    CONSTRAINT ck_app_ingest_obj CHECK (jsonb_typeof(ingest_cursor) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_app_name_trgm
    ON doc_generator.application USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_app_key
    ON doc_generator.application (key);
CREATE INDEX IF NOT EXISTS idx_app_repo
    ON doc_generator.application (repo_provider, repo_owner, repo_name);
CREATE INDEX IF NOT EXISTS idx_app_tags_gin
    ON doc_generator.application USING GIN (tags);
CREATE INDEX IF NOT EXISTS idx_app_languages_gin
    ON doc_generator.application USING GIN (languages);
CREATE INDEX IF NOT EXISTS idx_app_metadata_gin
    ON doc_generator.application USING GIN (metadata);
CREATE INDEX IF NOT EXISTS idx_app_created_brin
    ON doc_generator.application USING BRIN (created_at);

--changeset arch:002_node context:prod
--comment: Nodes
CREATE TABLE IF NOT EXISTS doc_generator.node
(
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT                  NOT NULL REFERENCES doc_generator.application (id) ON DELETE CASCADE,
    fqn            TEXT                    NOT NULL,
    name           TEXT,
    package        TEXT,
    kind           doc_generator.node_kind NOT NULL,
    lang           doc_generator.lang      NOT NULL,
    parent_id      BIGINT REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    file_path      TEXT,
    line_start     INT,
    line_end       INT,
    source_code    TEXT,
    doc_comment    TEXT,
    signature      TEXT,
    code_hash      TEXT,
    meta           JSONB                   NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ             NOT NULL DEFAULT now(),

    CONSTRAINT ux_node_app_fqn UNIQUE (application_id, fqn),
    CONSTRAINT ck_node_lines CHECK (
        (line_start IS NULL AND line_end IS NULL)
            OR (line_start IS NOT NULL AND line_end IS NOT NULL AND line_start <= line_end)
        ),
    CONSTRAINT ck_node_code_hash CHECK (code_hash IS NULL OR code_hash ~ '^[A-Fa-f0-9]{16,128}$')
);

CREATE INDEX IF NOT EXISTS idx_node_app_kind ON doc_generator.node (application_id, kind);
CREATE INDEX IF NOT EXISTS idx_node_app_lang ON doc_generator.node (application_id, lang);
CREATE INDEX IF NOT EXISTS idx_node_file ON doc_generator.node (application_id, file_path);
CREATE INDEX IF NOT EXISTS idx_node_parent ON doc_generator.node (application_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_node_app_kind_name ON doc_generator.node (application_id, kind, name);
CREATE INDEX IF NOT EXISTS idx_node_fqn_trgm ON doc_generator.node USING GIN (fqn gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_node_doc_tsv ON doc_generator.node USING GIN (to_tsvector('english', coalesce(doc_comment, '')));
CREATE INDEX IF NOT EXISTS idx_node_meta_gin ON doc_generator.node USING GIN (meta);
CREATE INDEX IF NOT EXISTS idx_node_created_brin ON doc_generator.node USING BRIN (created_at);

--changeset arch:002_library context:prod
--comment: Libraries
CREATE TABLE IF NOT EXISTS doc_generator.library
(
    id         BIGSERIAL PRIMARY KEY,
    coordinate TEXT    NOT NULL,
    group_id   TEXT    NOT NULL,
    artifact_id TEXT   NOT NULL,
    version    TEXT    NOT NULL,
    kind       TEXT,
    metadata   JSONB   NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_library_coordinate UNIQUE (coordinate)
);

CREATE INDEX IF NOT EXISTS idx_library_ga ON doc_generator.library (group_id, artifact_id);
CREATE INDEX IF NOT EXISTS idx_library_metadata_gin ON doc_generator.library USING GIN (metadata);

CREATE TABLE IF NOT EXISTS doc_generator.library_node
(
    id          BIGSERIAL PRIMARY KEY,
    library_id  BIGINT NOT NULL REFERENCES doc_generator.library (id) ON DELETE CASCADE,
    fqn         TEXT   NOT NULL,
    name        TEXT,
    package     TEXT,
    kind        doc_generator.node_kind NOT NULL,
    lang        doc_generator.lang      NOT NULL,
    parent_id   BIGINT REFERENCES doc_generator.library_node (id) ON DELETE CASCADE,
    file_path   TEXT,
    line_start  INT,
    line_end    INT,
    source_code TEXT,
    doc_comment TEXT,
    signature   TEXT,
    meta        JSONB  NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ux_library_node_lib_fqn UNIQUE (library_id, fqn),
    CONSTRAINT ck_library_node_lines CHECK (
        (line_start IS NULL AND line_end IS NULL)
            OR (line_start IS NOT NULL AND line_end IS NOT NULL AND line_start <= line_end)
        )
);

CREATE INDEX IF NOT EXISTS idx_library_node_lib_kind ON doc_generator.library_node (library_id, kind);
CREATE INDEX IF NOT EXISTS idx_library_node_lib_pkg ON doc_generator.library_node (library_id, package);
CREATE INDEX IF NOT EXISTS idx_library_node_fqn_trgm ON doc_generator.library_node USING GIN (fqn gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_library_node_meta_gin ON doc_generator.library_node USING GIN (meta);

--changeset arch:003_edge context:prod
--comment: Graph edges
CREATE TABLE IF NOT EXISTS doc_generator.edge
(
    src_id            BIGINT                  NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    dst_id            BIGINT                  NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    kind              doc_generator.edge_kind NOT NULL,
    evidence          JSONB                   NOT NULL DEFAULT '{}'::jsonb,
    explain_md        TEXT,
    confidence        NUMERIC(3, 2) CHECK (confidence BETWEEN 0 AND 1),
    relation_strength TEXT,
    created_at        TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ             NOT NULL DEFAULT now(),
    PRIMARY KEY (src_id, dst_id, kind)
);

CREATE INDEX IF NOT EXISTS idx_edge_src ON doc_generator.edge (src_id);
CREATE INDEX IF NOT EXISTS idx_edge_dst ON doc_generator.edge (dst_id);
CREATE INDEX IF NOT EXISTS idx_edge_kind ON doc_generator.edge (kind);
CREATE INDEX IF NOT EXISTS idx_edge_confidence ON doc_generator.edge (confidence);
CREATE INDEX IF NOT EXISTS idx_edge_explain_tsv
    ON doc_generator.edge USING GIN (to_tsvector('english', coalesce(explain_md, '')));
CREATE INDEX IF NOT EXISTS idx_edge_evidence_gin
    ON doc_generator.edge USING GIN (evidence);


--changeset arch:003_chunk context:prod
--comment: RAG chunks (DEMO: BYTEA instead of vector, no ivfflat index)
CREATE TABLE IF NOT EXISTS doc_generator.chunk
(
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT      NOT NULL REFERENCES doc_generator.application (id) ON DELETE CASCADE,
    node_id         BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    source          TEXT        NOT NULL,
    kind            TEXT,
    lang_detected   TEXT,
    content_raw     TEXT,
    content         TEXT        NOT NULL,
    content_tsv     tsvector GENERATED ALWAYS AS (doc_generator.make_ru_en_tsv(content)) STORED,
    content_hash    TEXT,
    token_count     INT,
    chunk_index     INT,
    span_lines      INT4RANGE,
    span_chars      INT8RANGE,
    title           TEXT,
    section_path    TEXT[]      NOT NULL DEFAULT '{}'::text[],
    uses_md         TEXT,
    used_by_md      TEXT,

    -- DEMO: BYTEA instead of vector(1024) — pgvector not available
    embedding       BYTEA,
    embed_model     TEXT,
    embed_ts        TIMESTAMPTZ,

    explain_md      TEXT,
    explain_quality JSONB       NOT NULL DEFAULT '{}'::jsonb,
    relations       JSONB       NOT NULL DEFAULT '[]'::jsonb,
    used_objects    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    pipeline        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    freshness_at    TIMESTAMPTZ,
    rank_boost      REAL        NOT NULL DEFAULT 1.0,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_chunk_source CHECK (source IN ('code', 'doc', 'sql', 'log')),
    CONSTRAINT ck_chunk_kind CHECK (kind IS NULL OR kind ~ '^[a-z][a-z0-9_\-]{0,63}$'),
    CONSTRAINT ck_chunk_hash CHECK (content_hash IS NULL OR content_hash ~ '^[A-Fa-f0-9]{16,128}$'),
    CONSTRAINT ck_chunk_rank_boost CHECK (rank_boost > 0)
);

CREATE INDEX IF NOT EXISTS idx_chunk_app_node ON doc_generator.chunk (application_id, node_id);
CREATE INDEX IF NOT EXISTS idx_chunk_app_source_kind ON doc_generator.chunk (application_id, source, kind);
CREATE INDEX IF NOT EXISTS idx_chunk_tsv ON doc_generator.chunk USING GIN (content_tsv);
CREATE INDEX IF NOT EXISTS idx_chunk_explain_tsv ON doc_generator.chunk USING GIN (to_tsvector('english', coalesce(explain_md, '')));
CREATE INDEX IF NOT EXISTS idx_chunk_uses_tsv ON doc_generator.chunk USING GIN (to_tsvector('english', coalesce(uses_md, '')));
CREATE INDEX IF NOT EXISTS idx_chunk_used_by_tsv ON doc_generator.chunk USING GIN (to_tsvector('english', coalesce(used_by_md, '')));
CREATE INDEX IF NOT EXISTS idx_chunk_relations_gin ON doc_generator.chunk USING GIN (relations);
CREATE INDEX IF NOT EXISTS idx_chunk_used_objects_gin ON doc_generator.chunk USING GIN (used_objects);
CREATE INDEX IF NOT EXISTS idx_chunk_section_path_gin ON doc_generator.chunk USING GIN (section_path);
-- DEMO: no ivfflat index (requires pgvector)
CREATE INDEX IF NOT EXISTS idx_chunk_created_brin ON doc_generator.chunk USING BRIN (created_at);
CREATE INDEX IF NOT EXISTS idx_chunk_freshness_brin ON doc_generator.chunk USING BRIN (freshness_at);

--changeset arch:004_node_doc context:prod
--comment: Canonical per-node docs
CREATE TABLE IF NOT EXISTS doc_generator.node_doc
(
    node_id      BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    locale       TEXT        NOT NULL DEFAULT 'ru',
    summary      TEXT,
    details      TEXT,
    params       JSONB,
    returns      JSONB,
    throws       JSONB,
    examples     TEXT,
    quality      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    source_kind  TEXT        NOT NULL DEFAULT 'manual',
    model_name   TEXT,
    model_meta   JSONB       NOT NULL DEFAULT '{}'::jsonb,
    evidence     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    updated_by   TEXT,
    summary_tsv  tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(summary, ''))) STORED,
    details_tsv  tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(details, ''))) STORED,
    examples_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(examples, ''))) STORED,
    is_published BOOLEAN     NOT NULL DEFAULT TRUE,
    published_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_node_doc PRIMARY KEY (node_id, locale),
    CONSTRAINT ck_node_doc_source_kind CHECK (source_kind IN ('manual','llm','import')),
    CONSTRAINT ck_node_doc_quality_obj CHECK (jsonb_typeof(quality) = 'object'),
    CONSTRAINT ck_node_doc_model_meta_obj CHECK (jsonb_typeof(model_meta) = 'object'),
    CONSTRAINT ck_node_doc_evidence_obj CHECK (jsonb_typeof(evidence) = 'object'),
    CONSTRAINT ck_node_doc_params_obj CHECK (params IS NULL OR jsonb_typeof(params) = 'object'),
    CONSTRAINT ck_node_doc_returns_obj CHECK (returns IS NULL OR jsonb_typeof(returns) = 'object'),
    CONSTRAINT ck_node_doc_throws_arr CHECK (throws IS NULL OR jsonb_typeof(throws) = 'array'),
    CONSTRAINT ck_node_doc_publish_consistency CHECK (
        (is_published = FALSE AND published_at IS NULL)
            OR (is_published = TRUE)
        )
);

CREATE INDEX IF NOT EXISTS idx_node_doc_locale ON doc_generator.node_doc (locale);
CREATE INDEX IF NOT EXISTS idx_node_doc_is_published ON doc_generator.node_doc (is_published);
CREATE INDEX IF NOT EXISTS idx_node_doc_published_at_brin ON doc_generator.node_doc USING BRIN (published_at);
CREATE INDEX IF NOT EXISTS idx_node_doc_summary_tsv ON doc_generator.node_doc USING GIN (summary_tsv);
CREATE INDEX IF NOT EXISTS idx_node_doc_details_tsv ON doc_generator.node_doc USING GIN (details_tsv);
CREATE INDEX IF NOT EXISTS idx_node_doc_examples_tsv ON doc_generator.node_doc USING GIN (examples_tsv);
