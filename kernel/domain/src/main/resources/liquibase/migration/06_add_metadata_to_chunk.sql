--changeset arch:006_add_metadata context:prod
--comment: Добавляем колонку metadata в таблицу chunk для совместимости с Spring AI PgVectorStore

ALTER TABLE doc_generator.chunk
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN doc_generator.chunk.metadata IS 'Метаданные для Spring AI PgVectorStore (JSONB).';

