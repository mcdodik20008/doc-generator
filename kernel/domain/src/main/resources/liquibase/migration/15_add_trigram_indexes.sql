--liquibase formatted sql

-- Включаем расширение pg_trgm для поддержки триграм индексов
-- Триграм индексы значительно ускоряют LIKE '%pattern%' запросы
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Создаем триграм GIN индекс для поля fqn в таблице node
-- Это ускорит запросы вида: WHERE fqn LIKE '%pattern%'
-- GIN индекс лучше подходит для текстового поиска чем GiST
CREATE INDEX IF NOT EXISTS idx_node_fqn_trigram
    ON doc_generator.node USING gin (fqn gin_trgm_ops);

-- Создаем триграм GIN индекс для поля name в таблице node
-- Ускорит поиск по частичному имени узла
CREATE INDEX IF NOT EXISTS idx_node_name_trigram
    ON doc_generator.node USING gin (name gin_trgm_ops);

-- Комментарии для документации
COMMENT ON INDEX doc_generator.idx_node_fqn_trigram IS
    'Trigram index for fast LIKE queries on node.fqn field';
COMMENT ON INDEX doc_generator.idx_node_name_trigram IS
    'Trigram index for fast LIKE queries on node.name field';
