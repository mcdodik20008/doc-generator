--liquibase formatted sql
--changeset arch:030_spring_ai_chat_memory context:prod,dev
--comment: Spring AI JDBC Chat Memory (совместимая схема)
CREATE TABLE IF NOT EXISTS doc_generator.SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(100) NOT NULL,
    type            VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL,
    metadata        JSONB,
    "timestamp"     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONV_TS_IDX
    ON doc_generator.SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");
--endChangeset
