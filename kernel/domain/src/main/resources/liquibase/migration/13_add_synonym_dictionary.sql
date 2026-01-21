--liquibase formatted sql

--changeset arch:013_add_synonym_dictionary context:prod
--comment: Добавление таблицы synonym_dictionary для семантического расширения запросов

CREATE TABLE doc_generator.synonym_dictionary
(
    id              BIGSERIAL PRIMARY KEY,
    
    -- Основные поля
    term            TEXT        NOT NULL,
    description     TEXT        NOT NULL,
    source_node_id  BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    
    -- Векторные эмбеддинги
    term_embedding  vector(1024),
    desc_embedding  vector(1024),
    
    -- Метаданные модели
    model_name      TEXT        NOT NULL,
    
    -- Служебное
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    CONSTRAINT ck_synonym_term_not_empty CHECK (length(trim(term)) > 0),
    CONSTRAINT ck_synonym_desc_not_empty CHECK (length(trim(description)) > 0)
);

COMMENT ON TABLE doc_generator.synonym_dictionary IS 'Словарь семантических пар (термин-описание) для расширения запросов RAG';
COMMENT ON COLUMN doc_generator.synonym_dictionary.term IS 'Технический термин или действие (напр., "вставка распределения")';
COMMENT ON COLUMN doc_generator.synonym_dictionary.description IS 'Смысловое пояснение из секции "Назначение"';
COMMENT ON COLUMN doc_generator.synonym_dictionary.source_node_id IS 'FK к node.id - источник документации';
COMMENT ON COLUMN doc_generator.synonym_dictionary.term_embedding IS 'Эмбеддинг поля term (размерность 1024)';
COMMENT ON COLUMN doc_generator.synonym_dictionary.desc_embedding IS 'Эмбеддинг поля description (размерность 1024)';
COMMENT ON COLUMN doc_generator.synonym_dictionary.model_name IS 'Название модели эмбеддингов (напр., nomic-embed-text-v1.5)';

-- Индексы для векторного поиска
CREATE INDEX IF NOT EXISTS idx_synonym_term_embedding_ivfflat 
    ON doc_generator.synonym_dictionary 
    USING ivfflat (term_embedding vector_cosine_ops) 
    WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_synonym_desc_embedding_ivfflat 
    ON doc_generator.synonym_dictionary 
    USING ivfflat (desc_embedding vector_cosine_ops) 
    WITH (lists = 100);

-- Индексы для связей и фильтрации
CREATE INDEX IF NOT EXISTS idx_synonym_source_node_id ON doc_generator.synonym_dictionary (source_node_id);
CREATE INDEX IF NOT EXISTS idx_synonym_model_name ON doc_generator.synonym_dictionary (model_name);

-- Индекс для обновления
CREATE INDEX IF NOT EXISTS idx_synonym_updated_brin ON doc_generator.synonym_dictionary USING BRIN (updated_at);
