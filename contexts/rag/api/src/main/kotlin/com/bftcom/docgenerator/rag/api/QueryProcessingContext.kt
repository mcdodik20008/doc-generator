package com.bftcom.docgenerator.rag.api

/**
 * Контекст обработки запроса, который передается через цепочку advisors
 */
data class QueryProcessingContext(
        val originalQuery: String,
        var currentQuery: String,
        val sessionId: String,
        val metadata: MutableMap<String, Any> = mutableMapOf(),
        val processingSteps: MutableList<ProcessingStep> = mutableListOf(),
) {
        fun addStep(step: ProcessingStep) {
                processingSteps.add(step)
        }

        fun updateQuery(newQuery: String) {
                metadata[QueryMetadataKeys.PREVIOUS_QUERY.key] = currentQuery
                currentQuery = newQuery
        }

        /**
         * Безопасный доступ к метаданным через enum ключ
         */
        fun <T> getMetadata(key: QueryMetadataKeys): T? {
                return metadata[key.key] as? T
        }

        /**
         * Безопасная установка метаданных через enum ключ
         */
        fun setMetadata(key: QueryMetadataKeys, value: Any) {
                metadata[key.key] = value
        }

        /**
         * Проверка наличия ключа в метаданных
         */
        fun hasMetadata(key: QueryMetadataKeys): Boolean {
                return metadata.containsKey(key.key)
        }
}

