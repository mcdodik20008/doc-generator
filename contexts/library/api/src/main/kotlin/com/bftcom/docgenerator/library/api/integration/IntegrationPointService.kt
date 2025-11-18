package com.bftcom.docgenerator.library.api.integration

import com.bftcom.docgenerator.domain.library.LibraryNode

/**
 * Сервис для работы с интеграционными точками.
 * Извлекает информацию об HTTP/Kafka/Camel вызовах из LibraryNode.
 */
interface IntegrationPointService {
    /**
     * Извлекает все интеграционные точки из LibraryNode.
     */
    fun extractIntegrationPoints(libraryNode: LibraryNode): List<IntegrationPoint>
    
    /**
     * Находит все методы библиотеки, которые являются родительскими клиентами.
     */
    fun findParentClients(libraryId: Long): List<LibraryNode>
    
    /**
     * Находит все методы, которые вызывают указанный URL.
     */
    fun findMethodsByUrl(url: String, libraryId: Long? = null): List<LibraryNode>
    
    /**
     * Находит все методы, которые используют указанный Kafka topic.
     */
    fun findMethodsByKafkaTopic(topic: String, libraryId: Long? = null): List<LibraryNode>
    
    /**
     * Находит все методы, которые используют указанный Camel URI.
     */
    fun findMethodsByCamelUri(uri: String, libraryId: Long? = null): List<LibraryNode>
    
    /**
     * Получает сводку по интеграционным точкам для метода.
     */
    fun getMethodIntegrationSummary(methodFqn: String, libraryId: Long): IntegrationMethodSummary?

    /**
     * Сводка по интеграционным точкам для метода.
     */
    data class IntegrationMethodSummary(
        val methodFqn: String,
        val isParentClient: Boolean,
        val httpEndpoints: List<IntegrationPoint.HttpEndpoint>,
        val kafkaTopics: List<IntegrationPoint.KafkaTopic>,
        val camelRoutes: List<IntegrationPoint.CamelRoute>,
        val hasRetry: Boolean,
        val hasTimeout: Boolean,
        val hasCircuitBreaker: Boolean,
    )
}
