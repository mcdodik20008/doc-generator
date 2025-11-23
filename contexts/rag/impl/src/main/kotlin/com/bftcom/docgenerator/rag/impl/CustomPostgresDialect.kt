package com.bftcom.docgenerator.rag.impl

import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect
import org.springframework.stereotype.Component

@Component
class CustomPostgresDialect : PostgresChatMemoryRepositoryDialect() {

    override fun getSelectMessagesSql(): String {
        return "SELECT content, type FROM $SCHEMA_NAME.$TABLE_NAME WHERE conversation_id = ? ORDER BY \"timestamp\""
    }

    override fun getInsertMessageSql(): String {
        return "INSERT INTO $SCHEMA_NAME.$TABLE_NAME (conversation_id, content, type, \"timestamp\") VALUES (?, ?, ?, ?)"
    }

    override fun getDeleteMessagesSql(): String {
        return "DELETE FROM $SCHEMA_NAME.$TABLE_NAME WHERE conversation_id = ?"
    }

    companion object {
        private const val TABLE_NAME = "spring_ai_chat_memory"
        private const val SCHEMA_NAME = "doc_generator"
    }
}