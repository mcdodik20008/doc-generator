--liquibase formatted sql

--changeset arch:014_add_synonym_status_to_node_doc context:prod
--comment: Добавление поля synonym_status для отслеживания прогресса обработки узлов в словарь синонимов

ALTER TABLE doc_generator.node_doc
    ADD COLUMN synonym_status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (synonym_status IN ('PENDING', 'PROCESSING', 'INDEXED', 'SKIPPED_HEURISTIC', 'SKIPPED_JUDGE', 'FAILED_LLM'));

COMMENT ON COLUMN doc_generator.node_doc.synonym_status IS 'Статус обработки узла для словаря синонимов: PENDING (ожидает первичной обработки), PROCESSING (в работе), INDEXED (успешно извлечены синонимы), SKIPPED_HEURISTIC (отсеяно эвристиками), SKIPPED_JUDGE (отсеяно LLM-судьей), FAILED_LLM (ошибка экстракции)';

-- Индекс для эффективной выборки батчей
CREATE INDEX IF NOT EXISTS idx_node_doc_synonym_status_updated 
    ON doc_generator.node_doc (synonym_status, updated_at)
    WHERE synonym_status IN ('PENDING', 'PROCESSING');
