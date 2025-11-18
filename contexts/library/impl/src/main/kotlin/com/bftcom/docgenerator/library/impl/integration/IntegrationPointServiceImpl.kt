package com.bftcom.docgenerator.library.impl.integration

import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.library.api.integration.IntegrationPoint
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Реализация сервиса для работы с интеграционными точками.
 */
@Service
class IntegrationPointServiceImpl(
    private val libraryNodeRepo: LibraryNodeRepository,
) : IntegrationPointService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Suppress("UNCHECKED_CAST")
    override fun extractIntegrationPoints(libraryNode: LibraryNode): List<IntegrationPoint> {
        val meta = libraryNode.meta
        val integrationMeta = meta["integrationAnalysis"] as? Map<String, Any> ?: return emptyList()

        val points = mutableListOf<IntegrationPoint>()
        val methodFqn = libraryNode.fqn

        // HTTP endpoints
        val urls = integrationMeta["urls"] as? List<String> ?: emptyList()
        val httpMethods = integrationMeta["httpMethods"] as? List<String> ?: emptyList()
        val hasRetry = integrationMeta["hasRetry"] as? Boolean ?: false
        val hasTimeout = integrationMeta["hasTimeout"] as? Boolean ?: false
        val hasCircuitBreaker = integrationMeta["hasCircuitBreaker"] as? Boolean ?: false

        // Создаем HTTP endpoints (может быть несколько URL для одного метода)
        for (url in urls) {
            for (httpMethod in httpMethods.ifEmpty { listOf(null) }) {
                points.add(
                    IntegrationPoint.HttpEndpoint(
                        url = url,
                        methodId = methodFqn,
                        httpMethod = httpMethod,
                        clientType = "Unknown", // можно улучшить, сохраняя clientType в метаданных
                        hasRetry = hasRetry,
                        hasTimeout = hasTimeout,
                        hasCircuitBreaker = hasCircuitBreaker,
                    ),
                )
            }
        }

        // Kafka topics
        val kafkaTopics = integrationMeta["kafkaTopics"] as? List<String> ?: emptyList()
        val kafkaCalls = integrationMeta["kafkaCalls"] as? List<Map<String, Any>> ?: emptyList()

        for (topic in kafkaTopics) {
            val call = kafkaCalls.firstOrNull { it["topic"] == topic }
            points.add(
                IntegrationPoint.KafkaTopic(
                    methodId = methodFqn,
                    topic = topic,
                    operation = call?.get("operation") as? String ?: "UNKNOWN",
                    clientType = call?.get("clientType") as? String ?: "Unknown",
                ),
            )
        }

        // Camel routes
        val camelUris = integrationMeta["camelUris"] as? List<String> ?: emptyList()
        val camelCalls = integrationMeta["camelCalls"] as? List<Map<String, Any>> ?: emptyList()

        for (uri in camelUris) {
            val call = camelCalls.firstOrNull { it["uri"] == uri }
            points.add(
                IntegrationPoint.CamelRoute(
                    methodId = methodFqn,
                    uri = uri,
                    endpointType = call?.get("endpointType") as? String,
                    direction = call?.get("direction") as? String ?: "UNKNOWN",
                ),
            )
        }

        return points
    }

    override fun findParentClients(libraryId: Long): List<LibraryNode> = libraryNodeRepo.findParentClientsByLibraryId(libraryId)

    override fun findMethodsByUrl(
        url: String,
        libraryId: Long?,
    ): List<LibraryNode> {
        // Обертываем URL в JSON строку для поиска в массиве
        val urlJson = "\"$url\""
        return libraryNodeRepo.findMethodsByUrl(urlJson, libraryId)
    }

    override fun findMethodsByKafkaTopic(
        topic: String,
        libraryId: Long?,
    ): List<LibraryNode> {
        // Обертываем topic в JSON строку для поиска в массиве
        val topicJson = "\"$topic\""
        return libraryNodeRepo.findMethodsByKafkaTopic(topicJson, libraryId)
    }

    override fun findMethodsByCamelUri(
        uri: String,
        libraryId: Long?,
    ): List<LibraryNode> {
        // Обертываем URI в JSON строку для поиска в массиве
        val uriJson = "\"$uri\""
        return libraryNodeRepo.findMethodsByCamelUri(uriJson, libraryId)
    }

    override fun getMethodIntegrationSummary(
        methodFqn: String,
        libraryId: Long,
    ): IntegrationPointService.IntegrationMethodSummary? {
        val node = libraryNodeRepo.findByLibraryIdAndFqn(libraryId, methodFqn) ?: return null

        val points = extractIntegrationPoints(node)
        val httpEndpoints = points.filterIsInstance<IntegrationPoint.HttpEndpoint>()
        val kafkaTopics = points.filterIsInstance<IntegrationPoint.KafkaTopic>()
        val camelRoutes = points.filterIsInstance<IntegrationPoint.CamelRoute>()

        val integrationMeta = (node.meta["integrationAnalysis"] as? Map<String, Any>)
        val isParentClient = integrationMeta?.get("isParentClient") as? Boolean ?: false
        val hasRetry = integrationMeta?.get("hasRetry") as? Boolean ?: false
        val hasTimeout = integrationMeta?.get("hasTimeout") as? Boolean ?: false
        val hasCircuitBreaker = integrationMeta?.get("hasCircuitBreaker") as? Boolean ?: false

        return IntegrationPointService.IntegrationMethodSummary(
            methodFqn = methodFqn,
            isParentClient = isParentClient,
            httpEndpoints = httpEndpoints,
            kafkaTopics = kafkaTopics,
            camelRoutes = camelRoutes,
            hasRetry = hasRetry,
            hasTimeout = hasTimeout,
            hasCircuitBreaker = hasCircuitBreaker,
        )
    }
}
