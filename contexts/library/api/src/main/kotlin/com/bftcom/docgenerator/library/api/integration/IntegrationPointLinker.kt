package com.bftcom.docgenerator.library.api.integration

import com.bftcom.docgenerator.domain.application.Application

/**
 * Компонент для создания связей (Edge) между методами приложения и интеграционными точками.
 *
 * Использует информацию из LibraryNode.meta.integrationAnalysis для создания:
 * - CALLS_HTTP edges между методами приложения и HTTP endpoints
 * - PRODUCES/CONSUMES edges между методами и Kafka topics
 * - и т.д.
 */
interface IntegrationPointLinker {
    /**
     * Создает связи между методами приложения и интеграционными точками из библиотек.
     *
     * Алгоритм:
     * 1. Находит все методы приложения, которые вызывают методы библиотек с интеграционными точками
     * 2. Создает Edge между методом приложения и интеграционной точкой
     * 3. Для HTTP: создает CALLS_HTTP edge
     * 4. Для Kafka: создает PRODUCES или CONSUMES edge
     * 5. Для Camel: создает соответствующие edges
     */
    fun linkIntegrationPoints(application: Application): IntegrationLinkResult

    /**
     * Результат создания связей интеграционных точек.
     */
    data class IntegrationLinkResult(
        val httpEdgesCreated: Int = 0,
        val kafkaEdgesCreated: Int = 0,
        val camelEdgesCreated: Int = 0,
        val errors: List<String> = emptyList(),
    )
}
