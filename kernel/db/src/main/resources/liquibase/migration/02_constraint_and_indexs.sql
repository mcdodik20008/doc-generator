-- уникальность ребра
alter table doc_generator.edge
    add constraint uq_edge unique (src_id, dst_id, kind);

-- ускорение поиска нод
create unique index if not exists ux_node_app_fqn on doc_generator.node(application_id, fqn);
create index if not exists ix_node_app_name on doc_generator.node(application_id, name);

-- если meta = jsonb
create index if not exists gin_node_meta on doc_generator.node using gin (meta jsonb_path_ops);
create index if not exists ix_node_meta_ownerfqn on doc_generator.node ((meta->>'ownerFqn'));
create index if not exists ix_node_meta_typesimple on doc_generator.node ((meta->>'typeSimple'));
