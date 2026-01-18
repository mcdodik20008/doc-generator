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

    override fun extractIntegrationPoints(libraryNode: LibraryNode): List<IntegrationPoint> {
        val integrationMeta = libraryNode.meta["integrationAnalysis"] as? Map<String, Any> ?: return emptyList()
        val methodFqn = libraryNode.fqn

        // Вспомогательная функция для извлечения списков
        fun <T> getList(key: String): List<T> = integrationMeta[key] as? List<T> ?: emptyList()

        val points = mutableListOf<IntegrationPoint>()

        // HTTP
        val urls = getList<String>("urls")
        val httpMethods = getList<String>("httpMethods").ifEmpty { listOf(null) }

        urls.forEach { url ->
            httpMethods.forEach { method ->
                points.add(IntegrationPoint.HttpEndpoint(
                    url = url,
                    methodId = methodFqn,
                    httpMethod = method,
                    clientType = integrationMeta["clientType"] as? String ?: "Unknown",
                    hasRetry = integrationMeta.flag("hasRetry"),
                    hasTimeout = integrationMeta.flag("hasTimeout"),
                    hasCircuitBreaker = integrationMeta.flag("hasCircuitBreaker")
                ))
            }
        }

        // Kafka
        getList<String>("kafkaTopics").forEach { topic ->
            val call = getList<Map<String, Any>>("kafkaCalls").find { it["topic"] == topic }
            points.add(IntegrationPoint.KafkaTopic(
                methodId = methodFqn,
                topic = topic,
                operation = call?.get("operation") as? String ?: "UNKNOWN",
                clientType = call?.get("clientType") as? String ?: "Unknown"
            ))
        }

        // Camel
        getList<String>("camelUris").forEach { uri ->
            val call = getList<Map<String, Any>>("camelCalls").find { it["uri"] == uri }
            points.add(IntegrationPoint.CamelRoute(
                methodId = methodFqn,
                uri = uri,
                endpointType = call?.get("endpointType") as? String,
                direction = call?.get("direction") as? String ?: "UNKNOWN"
            ))
        }

        return points
    }

    override fun getMethodIntegrationSummary(methodFqn: String, libraryId: Long): IntegrationPointService.IntegrationMethodSummary? {
        val node = libraryNodeRepo.findByLibraryIdAndFqn(libraryId, methodFqn) ?: return null
        val points = extractIntegrationPoints(node)
        val meta = node.meta["integrationAnalysis"] as? Map<String, Any>

        return IntegrationPointService.IntegrationMethodSummary(
            methodFqn = methodFqn,
            isParentClient = meta.flag("isParentClient"),
            httpEndpoints = points.filterIsInstance<IntegrationPoint.HttpEndpoint>(),
            kafkaTopics = points.filterIsInstance<IntegrationPoint.KafkaTopic>(),
            camelRoutes = points.filterIsInstance<IntegrationPoint.CamelRoute>(),
            hasRetry = meta.flag("hasRetry"),
            hasTimeout = meta.flag("hasTimeout"),
            hasCircuitBreaker = meta.flag("hasCircuitBreaker")
        )
    }

    // Вспомогательное расширение для чистоты кода
    private fun Map<String, Any>?.flag(key: String): Boolean = this?.get(key) as? Boolean ?: false

    // Для БД запросов - используем более безопасный способ формирования JSON-литерала
    private fun String.toJsonString() = "\"$this\""

    override fun findParentClients(libraryId: Long) = libraryNodeRepo.findParentClientsByLibraryId(libraryId)
    override fun findMethodsByUrl(url: String, libraryId: Long?) = libraryNodeRepo.findMethodsByUrl(url.toJsonString(), libraryId)
    override fun findMethodsByKafkaTopic(topic: String, libraryId: Long?) = libraryNodeRepo.findMethodsByKafkaTopic(topic.toJsonString(), libraryId)
    override fun findMethodsByCamelUri(uri: String, libraryId: Long?) = libraryNodeRepo.findMethodsByCamelUri(uri.toJsonString(), libraryId)

    override fun resolveIntegrationPointsTransitive(
        libraryNode: LibraryNode,
        maxDepth: Int,
        cache: MutableMap<Long, Set<IntegrationPoint>>,
        visiting: MutableSet<Long>,
    ): Set<IntegrationPoint> {
        val nodeId = libraryNode.id
        if (nodeId != null) {
            cache[nodeId]?.let { return it }
        }

        if (maxDepth <= 0) {
            return extractIntegrationPoints(libraryNode).toSet()
        }

        if (nodeId != null && !visiting.add(nodeId)) {
            log.warn("Detected library call cycle at {}", libraryNode.fqn)
            return emptySet()
        }

        val points = mutableSetOf<IntegrationPoint>()
        points += extractIntegrationPoints(libraryNode)

        val internalCalls =
            (libraryNode.meta["internalCalls"] as? List<*>)?.filterIsInstance<String>().orEmpty()

        internalCalls.forEach { calledFqn ->
            val calledNodes = libraryNodeRepo.findAllByFqn(calledFqn)
            if (calledNodes.isEmpty()) {
                log.warn("Library node not found for internal call: {}", calledFqn)
                return@forEach
            }
            calledNodes.forEach { callee ->
                points += resolveIntegrationPointsTransitive(callee, maxDepth - 1, cache, visiting)
            }
        }

        if (nodeId != null) {
            visiting.remove(nodeId)
            cache[nodeId] = points
        }

        return points
    }
}
