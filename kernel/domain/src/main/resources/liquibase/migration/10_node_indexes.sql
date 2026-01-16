--liquibase formatted sql

-- Ускоряет поиск нод для генерации
CREATE INDEX IF NOT EXISTS idx_node_kind_method
    ON doc_generator.node (id)
    WHERE kind = 'METHOD';

-- Ускоряет проверку наличия перевода
CREATE INDEX IF NOT EXISTS idx_node_doc_lookup
    ON doc_generator.node_doc (node_id, locale);
