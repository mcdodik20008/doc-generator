package com.bftcom.docgenerator.rag.api

/**
 * Контекст обработки запроса, который передается через цепочку advisors
 * Мутабельный класс для совместимости со старыми advisors
 */
class QueryProcessingContext(
        val originalQuery: String,
        var currentQuery: String,
        val sessionId: String,
        val metadata: MutableMap<String, Any> = mutableMapOf(),
        val processingSteps: MutableList<ProcessingStep> = mutableListOf(),
) {
        fun addStep(step: ProcessingStep): QueryProcessingContext {
                processingSteps.add(step)
                return this
        }

        fun updateQuery(newQuery: String): QueryProcessingContext {
                metadata[QueryMetadataKeys.PREVIOUS_QUERY.key] = currentQuery
                currentQuery = newQuery
                return this
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
        fun setMetadata(key: QueryMetadataKeys, value: Any): QueryProcessingContext {
                metadata[key.key] = value
                return this
        }

        /**
         * Установка метаданных по строковому ключу (для динамических ключей)
         */
        fun setMetadata(key: String, value: Any): QueryProcessingContext {
                metadata[key] = value
                return this
        }

        /**
         * Проверка наличия ключа в метаданных
         */
        fun hasMetadata(key: QueryMetadataKeys): Boolean {
                return metadata.containsKey(key.key)
        }

        /**
         * Создает копию контекста (для immutable использования в FSM)
         */
        fun copy(
                originalQuery: String = this.originalQuery,
                currentQuery: String = this.currentQuery,
                sessionId: String = this.sessionId,
                metadata: Map<String, Any> = this.metadata.toMap(),
                processingSteps: List<ProcessingStep> = this.processingSteps.toList(),
        ): QueryProcessingContext {
                return QueryProcessingContext(
                        originalQuery = originalQuery,
                        currentQuery = currentQuery,
                        sessionId = sessionId,
                        metadata = metadata.toMutableMap(),
                        processingSteps = processingSteps.toMutableList(),
                )
        }
}

