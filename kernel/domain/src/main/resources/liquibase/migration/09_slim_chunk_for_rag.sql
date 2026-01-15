--liquibase formatted sql

--changeset arch:009_slim_chunk_for_rag context:prod
--comment: Slim chunk to RAG-only (doc public/tech) - breaking, OK for empty DB

-- Drop indexes that depend on removed columns
DROP INDEX IF EXISTS doc_generator.idx_chunk_explain_tsv;
DROP INDEX IF EXISTS doc_generator.idx_chunk_uses_tsv;
DROP INDEX IF EXISTS doc_generator.idx_chunk_used_by_tsv;
DROP INDEX IF EXISTS doc_generator.idx_chunk_relations_gin;
DROP INDEX IF EXISTS doc_generator.idx_chunk_used_objects_gin;
DROP INDEX IF EXISTS doc_generator.idx_chunk_section_path_gin;
DROP INDEX IF EXISTS doc_generator.idx_chunk_freshness_brin;

-- Drop old constraints (if exist) before dropping columns they reference
ALTER TABLE doc_generator.chunk
    DROP CONSTRAINT IF EXISTS ck_chunk_rank_boost;

-- Slim columns
ALTER TABLE doc_generator.chunk
    DROP COLUMN IF EXISTS content_raw,
    DROP COLUMN IF EXISTS chunk_index,
    DROP COLUMN IF EXISTS span_lines,
    DROP COLUMN IF EXISTS span_chars,
    DROP COLUMN IF EXISTS title,
    DROP COLUMN IF EXISTS section_path,
    DROP COLUMN IF EXISTS uses_md,
    DROP COLUMN IF EXISTS used_by_md,
    DROP COLUMN IF EXISTS explain_md,
    DROP COLUMN IF EXISTS explain_quality,
    DROP COLUMN IF EXISTS relations,
    DROP COLUMN IF EXISTS used_objects,
    DROP COLUMN IF EXISTS pipeline,
    DROP COLUMN IF EXISTS freshness_at,
    DROP COLUMN IF EXISTS rank_boost;

-- Make locale explicit for doc chunks (needed for uniqueness)
ALTER TABLE doc_generator.chunk
    ALTER COLUMN lang_detected SET DEFAULT 'ru',
    ALTER COLUMN lang_detected SET NOT NULL;

-- Enforce doc chunk kinds (public/tech)
ALTER TABLE doc_generator.chunk
    ADD CONSTRAINT ck_chunk_doc_kind
        CHECK (source <> 'doc' OR kind IN ('public', 'tech'));

-- Idempotency: for doc chunks keep exactly one row per (node, variant, locale)
-- Note: lang_detected is NOT NULL, so uniqueness is stable.
CREATE UNIQUE INDEX IF NOT EXISTS uq_chunk_doc_unique
    ON doc_generator.chunk (application_id, node_id, source, kind, lang_detected);

