--liquibase formatted sql

ALTER TABLE IF EXISTS doc_generator.node DROP COLUMN IF EXISTS kind;
ALTER TABLE IF EXISTS doc_generator.edge DROP COLUMN IF EXISTS kind;

DROP TYPE IF EXISTS doc_generator.kind CASCADE;
DROP TYPE IF EXISTS doc_generator.kind CASCADE;

CREATE TYPE doc_generator.kind AS ENUM (
    'REPO',
    'MODULE','PACKAGE',
    'CLASS','INTERFACE','ENUM','RECORD',
    'METHOD','FIELD','EXCEPTION','TEST','MAPPER',
    'SERVICE','ENDPOINT','CLIENT',
    'TOPIC','JOB',
    'DB_TABLE','DB_VIEW','DB_QUERY',
    'SCHEMA','CONFIG','MIGRATION'
    );

CREATE TYPE doc_generator.kind AS ENUM (
    'CONTAINS','DEPENDS_ON',
    'IMPLEMENTS','EXTENDS','OVERRIDES','ANNOTATED_WITH',
    'CALLS','CALLS_CODE','THROWS','LOCKS',
    'CALLS_HTTP','CALLS_GRPC',
    'PRODUCES','CONSUMES',
    'QUERIES','READS','WRITES',
    'CONTRACTS_WITH','CONFIGURES',
    'CIRCUIT_BREAKER_TO','RETRIES_TO','TIMEOUTS_TO'
    );

ALTER TABLE doc_generator.node ADD COLUMN node_kind doc_generator.kind;
ALTER TABLE doc_generator.edge ADD COLUMN edge_kind doc_generator.kind;

ALTER TABLE doc_generator.edge
    ADD CONSTRAINT uq_edge UNIQUE (src_id, dst_id, kind);