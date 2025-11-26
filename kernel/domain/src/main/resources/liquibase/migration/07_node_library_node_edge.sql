--changeset arch:007_node_library_node_edge context:prod
--comment: Edges between application nodes and library nodes

CREATE TABLE IF NOT EXISTS doc_generator.node_library_node_edge
(
    node_id           BIGINT                  NOT NULL REFERENCES doc_generator.node (id) ON DELETE CASCADE,
    library_node_id   BIGINT                  NOT NULL REFERENCES doc_generator.library_node (id) ON DELETE CASCADE,
    kind              doc_generator.edge_kind NOT NULL,

    evidence          JSONB                   NOT NULL DEFAULT '{}'::jsonb,
    explain_md        TEXT,
    confidence        NUMERIC(3, 2) CHECK (confidence BETWEEN 0 AND 1),
    relation_strength TEXT,

    created_at        TIMESTAMPTZ             NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ             NOT NULL DEFAULT now(),

    PRIMARY KEY (node_id, library_node_id, kind)
);

COMMENT ON TABLE doc_generator.node_library_node_edge IS
    'Рёбра графа между узлами приложения (node) и узлами библиотек (library_node).';
COMMENT ON COLUMN doc_generator.node_library_node_edge.node_id IS
    'Узел приложения (источник связи).';
COMMENT ON COLUMN doc_generator.node_library_node_edge.library_node_id IS
    'Узел библиотеки (цель связи).';
COMMENT ON COLUMN doc_generator.node_library_node_edge.kind IS
    'Тип связи: CALLS_HTTP, PRODUCES, CONSUMES, DEPENDS_ON и т.д.';

-- Индексы
CREATE INDEX IF NOT EXISTS idx_node_library_node_edge_node ON doc_generator.node_library_node_edge (node_id);
CREATE INDEX IF NOT EXISTS idx_node_library_node_edge_library_node ON doc_generator.node_library_node_edge (library_node_id);
CREATE INDEX IF NOT EXISTS idx_node_library_node_edge_kind ON doc_generator.node_library_node_edge (kind);
CREATE INDEX IF NOT EXISTS idx_node_library_node_edge_evidence_gin ON doc_generator.node_library_node_edge USING GIN (evidence);

