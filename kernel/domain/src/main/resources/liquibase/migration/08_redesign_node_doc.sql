--liquibase formatted sql

--changeset arch:008_redesign_node_doc context:prod
--comment: Redesign node_doc to doc_public/doc_tech/doc_digest with model_meta (breaking, OK for empty DB)

-- Drop previous wide node_doc (was not used in code yet)
DROP TABLE IF EXISTS doc_generator.node_doc CASCADE;

CREATE TABLE doc_generator.node_doc
(
    node_id     BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    locale      TEXT        NOT NULL DEFAULT 'ru',

    -- Canonical variants
    doc_public  TEXT,
    doc_tech    TEXT,
    doc_digest  TEXT,

    -- Model/prompt/context provenance and build metadata
    model_meta  JSONB       NOT NULL DEFAULT '{}'::jsonb,

    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_node_doc PRIMARY KEY (node_id, locale),
    CONSTRAINT ck_node_doc_model_meta_obj CHECK (jsonb_typeof(model_meta) = 'object')
);

COMMENT ON TABLE doc_generator.node_doc IS 'Каноничная документация на узел (варианты public/tech/digest) + метаданные генерации в model_meta.';
COMMENT ON COLUMN doc_generator.node_doc.doc_public IS 'Текст для обывателя (понятно без кода).';
COMMENT ON COLUMN doc_generator.node_doc.doc_tech IS 'Технический текст для инженера (факты/ограничения/поведение).';
COMMENT ON COLUMN doc_generator.node_doc.doc_digest IS 'Строгий компактный дайджест (kv-lines) для агрегации контекста на верхних уровнях.';
COMMENT ON COLUMN doc_generator.node_doc.model_meta IS 'JSONB: prompt_id/model/temperature/context_budget/included/deps_missing/source_hashes и т.п.';

CREATE INDEX IF NOT EXISTS idx_node_doc_locale ON doc_generator.node_doc (locale);
CREATE INDEX IF NOT EXISTS idx_node_doc_updated_brin ON doc_generator.node_doc USING BRIN (updated_at);

