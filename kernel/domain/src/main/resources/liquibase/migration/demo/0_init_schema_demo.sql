CREATE SCHEMA IF NOT EXISTS doc_generator;

CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- pgvector (CREATE EXTENSION vector) is NOT available in embedded PG
CREATE EXTENSION IF NOT EXISTS unaccent;
