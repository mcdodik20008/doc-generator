--liquibase formatted sql

ALTER TYPE doc_generator.edge_kind ADD VALUE IF NOT EXISTS 'CALLS_CAMEL';
