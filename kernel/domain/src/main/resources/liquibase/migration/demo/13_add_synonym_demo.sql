--liquibase formatted sql

--changeset arch:013_add_synonym_dictionary context:prod
--comment: Synonym dictionary (DEMO: BYTEA instead of vector, no ivfflat indexes)

CREATE TABLE doc_generator.synonym_dictionary
(
    id              BIGSERIAL PRIMARY KEY,
    term            TEXT        NOT NULL,
    description     TEXT        NOT NULL,
    source_node_id  BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,

    -- DEMO: BYTEA instead of vector(1024)
    term_embedding  BYTEA,
    desc_embedding  BYTEA,

    model_name      TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_synonym_term_not_empty CHECK (length(trim(term)) > 0),
    CONSTRAINT ck_synonym_desc_not_empty CHECK (length(trim(description)) > 0)
);

-- DEMO: no ivfflat indexes (requires pgvector)
CREATE INDEX IF NOT EXISTS idx_synonym_source_node_id ON doc_generator.synonym_dictionary (source_node_id);
CREATE INDEX IF NOT EXISTS idx_synonym_model_name ON doc_generator.synonym_dictionary (model_name);
CREATE INDEX IF NOT EXISTS idx_synonym_updated_brin ON doc_generator.synonym_dictionary USING BRIN (updated_at);
