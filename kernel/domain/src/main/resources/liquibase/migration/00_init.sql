--liquibase formatted sql

--changeset arch:001_init context:prod
--comment: Doc Generator —  functions, enums, tables, indexes, triggers
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
COMMENT ON TYPE doc_generator.node_kind IS
    'Тип узла графа кода/системы:
    - MODULE: логический модуль/артефакт (gradle-модуль, jar)
    - PACKAGE: пакет/namespace
    - CLASS|INTERFACE|ENUM|RECORD: языковые сущности
    - FIELD: поле сущности
    - METHOD: метод/функция
    - ENDPOINT: HTTP/gRPC endpoint
    - TOPIC: брокерское событие (Kafka/NATS/Rabbit)
    - DBTABLE: таблица БД
    - MIGRATION: миграция схемы
    - CONFIG: конфигурационный объект/файл
    - JOB: плановая задача/джоб.';

CREATE TYPE doc_generator.edge_kind AS ENUM (
    'CALLS','READS','WRITES','QUERIES','PUBLISHES','CONSUMES','THROWS',
    'IMPLEMENTS','OVERRIDES','LOCKS','OPENTELEMETRY','USES_FEATURE','DEPENDS_ON'
    );
COMMENT ON TYPE doc_generator.edge_kind IS
    'Семантика ребра графа:
    - CALLS: src вызывает dst
    - READS|WRITES: I/O к цели
    - QUERIES: выполняет запрос
    - PUBLISHES|CONSUMES: pub/sub
    - THROWS: генерирует исключение
    - IMPLEMENTS|OVERRIDES: реализация/переопределение
    - LOCKS: блокировки
    - OPENTELEMETRY: связь по трейсам
    - USES_FEATURE: фича-тоггл
    - DEPENDS_ON: общая зависимость.';

CREATE TYPE doc_generator.lang AS ENUM ('kotlin','java','sql','yaml','md','other');
COMMENT ON TYPE doc_generator.lang IS
    'Язык исходника: kotlin|java|sql|yaml|md|other.';

--changeset arch:000_app context:prod
--comment: Applications registry with repo/monorepo support, indexing state, ownership, RAG/ANN settings
CREATE TABLE doc_generator.application
(
    id                BIGSERIAL PRIMARY KEY,

    -- Идентичность
    key               TEXT        NOT NULL UNIQUE,               -- человекочитаемый slug
    name              TEXT        NOT NULL,                      -- отображаемое имя
    description       TEXT,                                      -- краткое описание/назначение

    -- Репозиторий / монорепо
    repo_url          TEXT,                                      -- полный URL (https/ssh)
    repo_provider     TEXT,                                      -- github|gitlab|bitbucket|gitea|other
    repo_owner        TEXT,                                      -- org/namespace
    repo_name         TEXT,                                      -- имя репозитория
    monorepo_path     TEXT,                                      -- подкаталог сервиса в монорепо (если есть)
    default_branch    TEXT        NOT NULL DEFAULT 'main',

    -- Индексация/сканирование
    last_commit_sha   TEXT,                                      -- последний проиндексированный commit
    last_indexed_at   TIMESTAMPTZ,                               -- когда завершили индексацию
    last_index_status TEXT,                                      -- success|failed|partial|running
    last_index_error  TEXT,                                      -- сообщение об ошибке (если было)
    ingest_cursor     JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- курсоры инкрементальной индексации/импорта

    -- Организация/владение/категоризация
    owners            JSONB       NOT NULL DEFAULT '[]'::jsonb,  -- [{"type":"team","id":"platform"}, {"type":"user","email":"dev@..."}]
    contacts          JSONB       NOT NULL DEFAULT '[]'::jsonb,  -- [{"kind":"slack","value":"#alerts"}, {"kind":"email","value":"..."}]
    tags              TEXT[]      NOT NULL DEFAULT '{}'::text[], -- произвольные теги (domain=data, env=prod и т.п.)
    languages         TEXT[]      NOT NULL DEFAULT '{}'::text[], -- основные языки проекта: ["kotlin","sql","yaml"]

    -- Настройки RAG/эмбеддингов/ANN (per-app overrides)
    embedding_model   TEXT,                                      -- например: mxbai-embed-large
    embedding_dim     INT         NOT NULL DEFAULT 1024,         -- ожидаемая размерность вектора в chunk.emb
    ann_index_params  JSONB       NOT NULL DEFAULT '{
      "method": "ivfflat",
      "lists": 100
    }'::jsonb,
    rag_prefs         JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- {"priority":["doc","code"],"reranker":"colbert",...}

    -- Политики/ретенция
    retention_days    INT,                                       -- через сколько дней чистим устаревшие чанки/логи
    pii_policy        JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- правила по PII/секретам (маскирование/skip)

    metadata          JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- любые прочие метаданные
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Валидации
    CONSTRAINT ck_app_key_slug CHECK (key ~ '^[a-z0-9][a-z0-9\\-]{2,64}$'),
    CONSTRAINT ck_app_embedding_dim CHECK (embedding_dim > 0),
    CONSTRAINT ck_app_last_index_status CHECK (last_index_status IS NULL OR
                                               last_index_status IN ('success', 'failed', 'partial', 'running', 'queued')),
    CONSTRAINT ck_app_repo_provider CHECK (repo_provider IS NULL OR
                                           repo_provider IN ('github', 'gitlab', 'bitbucket', 'gitea', 'other')),

    -- Лёгкая форма JSON
    CONSTRAINT ck_app_owners_arr CHECK (jsonb_typeof(owners) = 'array'),
    CONSTRAINT ck_app_contacts_arr CHECK (jsonb_typeof(contacts) = 'array'),
    CONSTRAINT ck_app_ann_obj CHECK (jsonb_typeof(ann_index_params) = 'object'),
    CONSTRAINT ck_app_rag_obj CHECK (jsonb_typeof(rag_prefs) = 'object'),
    CONSTRAINT ck_app_ingest_obj CHECK (jsonb_typeof(ingest_cursor) = 'object')
);

COMMENT ON TABLE doc_generator.application IS 'Реестр приложений/микросервисов (в т.ч. монорепо-пути), состояние индексации, владельцы и RAG/ANN настройки.';
COMMENT ON COLUMN doc_generator.application.key IS 'Стабильный slug (используется в API, напр. X-App-Key). Формат: ^[a-z0-9][a-z0-9-]{2,64}$.';
COMMENT ON COLUMN doc_generator.application.monorepo_path IS 'Подкаталог сервиса в монорепозитории (если проект часть монорепо).';
COMMENT ON COLUMN doc_generator.application.last_commit_sha IS 'Последний проиндексированный коммит (для инкрементальной индексации).';
COMMENT ON COLUMN doc_generator.application.ingest_cursor IS 'Технические курсоры/offset’ы для постраничного импорта из SCM/CI/Docs.';
COMMENT ON COLUMN doc_generator.application.embedding_dim IS 'Размерность векторов для этого приложения (должна соответствовать chunk.emb).';
COMMENT ON COLUMN doc_generator.application.ann_index_params IS 'Параметры ANN-индекса по умолчанию (например {"method":"ivfflat","lists":100}).';
COMMENT ON COLUMN doc_generator.application.retention_days IS 'Ретенция данных (например, автосбор мусора по старым лог-чанкам).';

-- Индексы (поиск и админка)
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
--comment: Nodes with hierarchy, fast search fields, and code change detection
CREATE TABLE IF NOT EXISTS doc_generator.node
(
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT                  NOT NULL REFERENCES doc_generator.application (id) ON DELETE CASCADE,

    -- Идентификация
    fqn            TEXT                    NOT NULL, -- уникален внутри application_id
    name           TEXT,                             -- короткое имя символа (без пакета), напр. "getUser"
    package        TEXT,                             -- пакет/namespace, напр. "com.acme.user"

    kind           doc_generator.node_kind NOT NULL,
    lang           doc_generator.lang      NOT NULL,

    -- Иерархия (контейнер → дочерний)
    parent_id      BIGINT REFERENCES doc_generator.node (id) ON DELETE CASCADE,

    -- Исходник/расположение
    file_path      TEXT,                             -- путь в репо
    line_start     INT,
    line_end       INT,
    source_code    TEXT,                             -- полное тело сущности
    doc_comment    TEXT,                             -- JavaDoc/KDoc/docstring/SQL

    -- Сигнатура и контроль изменений
    signature      TEXT,                             -- для METHOD/ENDPOINT и т.п. (напр. "(String,int):User")
    code_hash      TEXT,                             -- SHA-256/xxhash исходника (source_code) для дельт

    -- Произвольные атрибуты
    meta           JSONB                   NOT NULL DEFAULT '{}'::jsonb,

    created_at     TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ             NOT NULL DEFAULT now(),

    -- Уникальности/валидации
    CONSTRAINT ux_node_app_fqn UNIQUE (application_id, fqn),
    CONSTRAINT ck_node_lines CHECK (
        (line_start IS NULL AND line_end IS NULL)
            OR (line_start IS NOT NULL AND line_end IS NOT NULL AND line_start <= line_end)
        ),
    CONSTRAINT ck_node_code_hash CHECK (code_hash IS NULL OR code_hash ~ '^[A-Fa-f0-9]{16,128}$')
);

-- ===== Table: node =====
COMMENT ON TABLE doc_generator.node IS
    'Узел графа: класс/метод/таблица/эндпойнт и т.п. Имеет иерархию через parent_id (PACKAGE→CLASS→METHOD/FIELD).';
COMMENT ON COLUMN doc_generator.node.name IS 'Короткое имя символа (без пакета).';
COMMENT ON COLUMN doc_generator.node.package IS 'Пакет/namespace (для быстрого фильтра).';
COMMENT ON COLUMN doc_generator.node.parent_id IS 'Контейнерский узел (родитель) — для дерева символов.';
COMMENT ON COLUMN doc_generator.node.signature IS 'Сигнатура символа: для методов — параметры/return; для эндпойнтов — HTTP-метод+путь.';
COMMENT ON COLUMN doc_generator.node.code_hash IS 'Хэш исходника (например, SHA-256 hex) для детекции изменений.';
COMMENT ON COLUMN doc_generator.node.meta IS 'JSONB-атрибуты: visibility/modifiers/annotations/generics/etc.';

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
--comment: Libraries (jar artifacts) and their internal nodes
CREATE TABLE IF NOT EXISTS doc_generator.library
(
    id         BIGSERIAL PRIMARY KEY,

    -- Идентичность артефакта
    coordinate TEXT    NOT NULL, -- groupId:artifactId:version
    group_id   TEXT    NOT NULL,
    artifact_id TEXT   NOT NULL,
    version    TEXT    NOT NULL,

    kind       TEXT,             -- internal|external|system и т.п.

    metadata   JSONB   NOT NULL DEFAULT '{}'::jsonb,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ux_library_coordinate UNIQUE (coordinate)
);

COMMENT ON TABLE doc_generator.library IS
    'Библиотечный артефакт (jar): groupId:artifactId:version.';

CREATE INDEX IF NOT EXISTS idx_library_ga ON doc_generator.library (group_id, artifact_id);
CREATE INDEX IF NOT EXISTS idx_library_metadata_gin ON doc_generator.library USING GIN (metadata);

CREATE TABLE IF NOT EXISTS doc_generator.library_node
(
    id          BIGSERIAL PRIMARY KEY,
    library_id  BIGINT NOT NULL REFERENCES doc_generator.library (id) ON DELETE CASCADE,

    -- Идентификация внутри библиотеки
    fqn         TEXT   NOT NULL,
    name        TEXT,
    package     TEXT,

    kind        doc_generator.node_kind NOT NULL,
    lang        doc_generator.lang      NOT NULL,

    -- Иерархия
    parent_id   BIGINT REFERENCES doc_generator.library_node (id) ON DELETE CASCADE,

    -- Расположение внутри артефакта (условное)
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

COMMENT ON TABLE doc_generator.library_node IS
    'Узел внутри библиотечного артефакта (jar): класс/метод/тип и т.п.';

CREATE INDEX IF NOT EXISTS idx_library_node_lib_kind ON doc_generator.library_node (library_id, kind);
CREATE INDEX IF NOT EXISTS idx_library_node_lib_pkg ON doc_generator.library_node (library_id, package);
CREATE INDEX IF NOT EXISTS idx_library_node_fqn_trgm ON doc_generator.library_node USING GIN (fqn gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_library_node_meta_gin ON doc_generator.library_node USING GIN (meta);

--changeset arch:003_edge context:prod
--comment: Graph edges with structured evidence and LLM explanation
CREATE TABLE IF NOT EXISTS doc_generator.edge
(
    src_id            BIGINT                  NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    dst_id            BIGINT                  NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    kind              doc_generator.edge_kind NOT NULL,

    evidence          JSONB                   NOT NULL DEFAULT '{}'::jsonb, -- структурные доказательства
    explain_md        TEXT,                                                 -- LLM-трактовка связи (Markdown)
    confidence        NUMERIC(3, 2) CHECK (confidence BETWEEN 0 AND 1),     -- итоговая уверенность (аггрегат из evidence)
    relation_strength TEXT,                                                 -- weak|normal|strong — для визуализации

    created_at        TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ             NOT NULL DEFAULT now(),

    PRIMARY KEY (src_id, dst_id, kind)
);

COMMENT ON TABLE doc_generator.edge IS
    'Рёбра графа между узлами (src → dst) с типом связи и доказательной базой.';
COMMENT ON COLUMN doc_generator.edge.explain_md IS
    'LLM-трактовка связи: короткое текстовое объяснение, почему src связан с dst.';
COMMENT ON COLUMN doc_generator.edge.confidence IS
    'Числовая уверенность в корректности связи (0..1).';
COMMENT ON COLUMN doc_generator.edge.relation_strength IS
    'Сила связи (weak|normal|strong) — используется для визуализации графа.';
COMMENT ON COLUMN doc_generator.edge.evidence IS ' {
  "sources": [
    {
      "file": "src/main/kotlin/com/acme/user/UserService.kt",
      "line_start": 42,
      "line_end": 48,
      "commit": "af4b2e1",
      "repo": "user-service"
    },
    {
      "file": "src/main/kotlin/com/acme/user/UserRepository.kt",
      "lines": [112, 113],
      "commit": "af4b2e1"
    }
  ],
  "snippets": [
    "userRepository.findById(id)",
    "return UserResponse.from(user)"
  ],
  "extractor": {
    "name": "StaticAnalyzer",
    "version": "1.3.0",
    "model": "gpt4-code-analyzer"
  },
  "created_by": "doc-agent",
  "created_at": "2025-10-10T21:00:00Z",
  "confidence": 0.93,
  "direction": "forward",
  "explanation": "Method UserService.getUser() вызывает UserRepository.findById() для загрузки сущности из базы данных."
}';

-- Индексы
CREATE INDEX IF NOT EXISTS idx_edge_src ON doc_generator.edge (src_id);
CREATE INDEX IF NOT EXISTS idx_edge_dst ON doc_generator.edge (dst_id);
CREATE INDEX IF NOT EXISTS idx_edge_kind ON doc_generator.edge (kind);
CREATE INDEX IF NOT EXISTS idx_edge_confidence ON doc_generator.edge (confidence);
CREATE INDEX IF NOT EXISTS idx_edge_explain_tsv
    ON doc_generator.edge USING GIN (to_tsvector('english', coalesce(explain_md, '')));
CREATE INDEX IF NOT EXISTS idx_edge_evidence_gin
    ON doc_generator.edge USING GIN (evidence);


--changeset arch:003_chunk context:prod
--comment: RAG chunks with provenance, dedup, hierarchy context and hybrid-search aids
CREATE TABLE IF NOT EXISTS doc_generator.chunk
(
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT      NOT NULL REFERENCES doc_generator.application (id) ON DELETE CASCADE,
    node_id         BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,

    -- тип/источник чанка
    source          TEXT        NOT NULL,                      -- 'code'|'doc'|'sql'|'log'
    kind            TEXT,                                      -- подтип: 'summary'|'explanation'|'docstring'|'comment'|'ddl'|'endpoint'|...
    lang_detected   TEXT,                                      -- auto: 'ru'|'en'|...

    -- контент и дедуп
    content_raw     TEXT,                                      -- исходный текст (до нормализации)
    content         TEXT        NOT NULL,                      -- нормализованный/шлифованный текст
    content_tsv     tsvector GENERATED ALWAYS AS (doc_generator.make_ru_en_tsv(content)) STORED,
    content_hash    TEXT,                                      -- hex(SHA-256) для дедуп/идемпотентности
    token_count     INT,                                       -- кол-во токенов (для бюджетов и диагностики)

    -- позиция/границы в исходнике
    chunk_index     INT,                                       -- порядковый номер в разбиении узла
    span_lines      INT4RANGE,                                 -- [line_start, line_end]
    span_chars      INT8RANGE,                                 -- [char_start, char_end] в source_code

    -- контекст (иерархия/заголовки/пути)
    title           TEXT,                                      -- локальный заголовок/имя секции/метода
    section_path    TEXT[]      NOT NULL DEFAULT '{}'::text[], -- {"README","1. Введение","1.2 Ограничения"} / {"com.acme","user","UserService"}
    uses_md         TEXT,                                      -- человекочит. как мы используем зависимости (Markdown)
    used_by_md      TEXT,                                      -- человекочит. где нас используют (Markdown)

    -- вектор и модель
    embedding       vector(1024),                              -- эмбеддинг
    embed_model     TEXT,                                      -- напр. 'mxbai-embed-large'
    embed_ts        TIMESTAMPTZ,                               -- когда посчитан эмбеддинг

    -- трактовка и качество
    explain_md      TEXT,                                      -- LLM-объяснение чанка (Markdown)
    explain_quality JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- {"completeness":..,"truthfulness":..,"helpfulness":..,"model":"...","ts":"..."}

    -- связи (машиночитаемо)
    relations       JSONB       NOT NULL DEFAULT '[]'::jsonb,  -- [{"kind":"CALLS","dst_node_id":123,"confidence":0.9}, ...]
    used_objects    JSONB       NOT NULL DEFAULT '[]'::jsonb,  -- [{"ref_type":"NODE","node_id":456},{"ref_type":"LIB",...}]

    -- происхождение пайплайна
    pipeline        JSONB       NOT NULL DEFAULT '{}'::jsonb,  -- {"chunker":"CodeTopological","version":"1.2","params":{"overlap":40},"commit":"abc123"}
    freshness_at    TIMESTAMPTZ,                               -- "свежесть" контента (дата файла/коммита)
    rank_boost      REAL        NOT NULL DEFAULT 1.0,          -- ручной множитель ранга (для UI/ручной коррекции)

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_chunk_source CHECK (source IN ('code', 'doc', 'sql', 'log')),
    CONSTRAINT ck_chunk_kind CHECK (kind IS NULL OR kind ~ '^[a-z][a-z0-9_\\-]{0,63}$'),
    CONSTRAINT ck_chunk_hash CHECK (content_hash IS NULL OR content_hash ~ '^[A-Fa-f0-9]{16,128}$'),
    CONSTRAINT ck_chunk_rank_boost CHECK (rank_boost > 0)
);

-- Комментарии
COMMENT ON TABLE doc_generator.chunk IS 'RAG-чанки с дедупом, контекстом и провенансом для гибридного поиска.';
COMMENT ON COLUMN doc_generator.chunk.kind IS 'Подтип источника (summary/explanation/docstring/ddl/endpoint/...).';
COMMENT ON COLUMN doc_generator.chunk.section_path IS 'Иерархия секций/пакетов/заголовков для лучшего контекста и фильтрации.';
COMMENT ON COLUMN doc_generator.chunk.content_hash IS 'Хэш контента для дедупликации и идемпотентности импорта.';
COMMENT ON COLUMN doc_generator.chunk.span_lines IS '[line_start, line_end] в исходном файле/узле (включительно-исключительно семантика range).';
COMMENT ON COLUMN doc_generator.chunk.span_chars IS '[char_start, char_end] в исходном тексте узла.';
COMMENT ON COLUMN doc_generator.chunk.embed_model IS 'Название модели эмбеддинга (пер-апп override).';
COMMENT ON COLUMN doc_generator.chunk.pipeline IS 'Происхождение чанка: чанкер/параметры/commit/агенты. Используется для отладки и воспроизводимости.';
COMMENT ON COLUMN doc_generator.chunk.freshness_at IS 'Момент, к которому относится материал чанка (для time-decay в ранжировании).';
COMMENT ON COLUMN doc_generator.chunk.rank_boost IS 'Ручной множитель при ранжировании (например, повышать важные секции).';

-- Индексы (фильтры/поиск)
CREATE INDEX IF NOT EXISTS idx_chunk_app_node ON doc_generator.chunk (application_id, node_id);
CREATE INDEX IF NOT EXISTS idx_chunk_app_source_kind ON doc_generator.chunk (application_id, source, kind);
CREATE INDEX IF NOT EXISTS idx_chunk_tsv ON doc_generator.chunk USING GIN (content_tsv);
CREATE INDEX IF NOT EXISTS idx_chunk_explain_tsv ON doc_generator.chunk USING GIN (to_tsvector('english', coalesce(explain_md, '')));
CREATE INDEX IF NOT EXISTS idx_chunk_uses_tsv ON doc_generator.chunk USING GIN (to_tsvector('english', coalesce(uses_md, '')));
CREATE INDEX IF NOT EXISTS idx_chunk_used_by_tsv ON doc_generator.chunk USING GIN (to_tsvector('english', coalesce(used_by_md, '')));
CREATE INDEX IF NOT EXISTS idx_chunk_relations_gin ON doc_generator.chunk USING GIN (relations);
CREATE INDEX IF NOT EXISTS idx_chunk_used_objects_gin ON doc_generator.chunk USING GIN (used_objects);
CREATE INDEX IF NOT EXISTS idx_chunk_section_path_gin ON doc_generator.chunk USING GIN (section_path);
CREATE INDEX IF NOT EXISTS idx_chunk_emb_ivfflat ON doc_generator.chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists=100);
CREATE INDEX IF NOT EXISTS idx_chunk_created_brin ON doc_generator.chunk USING BRIN (created_at);
CREATE INDEX IF NOT EXISTS idx_chunk_freshness_brin ON doc_generator.chunk USING BRIN (freshness_at);

--changeset arch:004_node_doc context:prod
--comment: Canonical per-node docs with i18n, provenance, validation, search, and revisions
CREATE TABLE IF NOT EXISTS doc_generator.node_doc
(
    node_id      BIGINT      NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    locale       TEXT        NOT NULL DEFAULT 'ru',        -- 'ru' | 'en' | 'ru-RU' ...

    summary      TEXT,                                     -- кратко «что делает»
    details      TEXT,                                     -- развёрнутое описание (Markdown)
    params       JSONB,                                    -- {"arg": {"type":"T","desc":"..."}, ...}
    returns      JSONB,                                    -- {"type":"T","desc":"..."}
    throws       JSONB,                                    -- [{"type":"E","desc":"..."}, ...]
    examples     TEXT,                                     -- Markdown-код/фрагменты
    quality      JSONB       NOT NULL DEFAULT '{}'::jsonb, -- {"completeness":..,"truthfulness":..,"helpfulness":..}

    -- происхождение/авторство
    source_kind  TEXT        NOT NULL DEFAULT 'manual',    -- 'manual' | 'llm' | 'import'
    model_name   TEXT,                                     -- если source_kind='llm' (напр. qwen2.5:7b)
    model_meta   JSONB       NOT NULL DEFAULT '{}'::jsonb, -- {"temperature":0.2,"prompt_id":"..."}
    evidence     JSONB       NOT NULL DEFAULT '{}'::jsonb, -- ссылки на chunks/коммиты/файлы
    updated_by   TEXT,                                     -- автор последней правки (login)

    -- поисковые удобства
    summary_tsv  tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(summary, ''))) STORED,
    details_tsv  tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(details, ''))) STORED,
    examples_tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', coalesce(examples, ''))) STORED,

    is_published BOOLEAN     NOT NULL DEFAULT TRUE,        -- готово к показу
    published_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- ключи/валидации
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

-- Индексы
CREATE INDEX IF NOT EXISTS idx_node_doc_locale ON doc_generator.node_doc (locale);
CREATE INDEX IF NOT EXISTS idx_node_doc_is_published ON doc_generator.node_doc (is_published);
CREATE INDEX IF NOT EXISTS idx_node_doc_published_at_brin ON doc_generator.node_doc USING BRIN (published_at);
CREATE INDEX IF NOT EXISTS idx_node_doc_summary_tsv ON doc_generator.node_doc USING GIN (summary_tsv);
CREATE INDEX IF NOT EXISTS idx_node_doc_details_tsv ON doc_generator.node_doc USING GIN (details_tsv);
CREATE INDEX IF NOT EXISTS idx_node_doc_examples_tsv ON doc_generator.node_doc USING GIN (examples_tsv);
