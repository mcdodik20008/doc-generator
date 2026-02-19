package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.dto.*
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.CrossAppGraphService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Реализация сервиса для построения cross-app графов.
 *
 * Находит интеграционные точки (виртуальные узлы с meta.synthetic=true)
 * и строит граф связей между приложениями через эти точки.
 */
@Service
@Transactional(readOnly = true)
class CrossAppGraphServiceImpl(
    private val applicationRepo: ApplicationRepository,
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
) : CrossAppGraphService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun buildCrossAppGraph(
        applicationIds: List<Long>,
        integrationTypes: Set<IntegrationType>,
        limit: Int
    ): CrossAppGraphResponse {
        log.info("Building cross-app graph for applications: {}, types: {}", applicationIds, integrationTypes)

        // 1. Load applications
        val apps = if (applicationIds.isEmpty()) {
            applicationRepo.findAll()
        } else {
            applicationRepo.findAllById(applicationIds)
        }

        if (apps.isEmpty()) {
            log.warn("No applications found for IDs: {}", applicationIds)
            return CrossAppGraphResponse(
                nodes = emptyList(),
                edges = emptyList(),
                statistics = CrossAppStatistics(0, 0, 0, 0, 0)
            )
        }

        val appIds = apps.map { requireNotNull(it.id) }
        log.debug("Found {} applications: {}", apps.size, apps.map { it.key })

        // 2. Find integration nodes (synthetic nodes with kind=ENDPOINT or TOPIC)
        val integrationNodes = nodeRepo.findAllByApplicationIdInAndKindIn(
            appIds,
            setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)
        ).filter { node ->
            // Filter by synthetic flag
            val isSynthetic = node.meta["synthetic"] as? Boolean ?: false
            if (!isSynthetic) return@filter false

            // Filter by integration type
            if (integrationTypes.isEmpty()) return@filter true

            matchesIntegrationType(node.fqn, integrationTypes)
        }

        log.debug("Found {} integration nodes", integrationNodes.size)

        // 3. Group integration nodes by FQN (same endpoint/topic in different apps)
        val nodesByFqn = integrationNodes.groupBy { it.fqn }
        log.debug("Grouped into {} unique integration points", nodesByFqn.size)

        // 4. Build cross-app nodes (applications + integration points)
        val crossAppNodes = mutableListOf<CrossAppNode>()
        val crossAppEdges = mutableListOf<CrossAppEdge>()

        // Add application nodes
        apps.forEach { app ->
            crossAppNodes.add(
                CrossAppNode(
                    id = "app:${app.id}",
                    label = app.name ?: app.key,
                    kind = "APPLICATION",
                    metadata = mapOf(
                        "key" to app.key,
                        "description" to (app.description ?: "")
                    )
                )
            )
        }

        // Statistics counters
        var httpCount = 0
        var kafkaCount = 0
        var camelCount = 0
        val edgeCountMap = mutableMapOf<String, Int>() // key: "appId:integrationId:kind"

        // 5. Process each unique integration point
        nodesByFqn.entries.take(limit).forEach { (fqn, nodes) ->
            val representative = nodes.first()
            val integrationKind = representative.kind.name
            val integrationId = "integration:$integrationKind:$fqn"

            // Count by type
            when {
                fqn.startsWith("infra:http:") -> httpCount++
                fqn.startsWith("infra:kafka:") -> kafkaCount++
                fqn.startsWith("infra:camel:") -> camelCount++
            }

            // Create integration point node
            val label = extractIntegrationLabel(fqn, representative.name)
            crossAppNodes.add(
                CrossAppNode(
                    id = integrationId,
                    label = label,
                    kind = integrationKind,
                    metadata = mapOf(
                        "fqn" to fqn,
                        "applicationsCount" to nodes.size
                    )
                )
            )

            // 6. Find edges connecting methods to this integration point
            nodes.forEach { node ->
                val nodeId = requireNotNull(node.id) { "Node must have ID" }
                val appId = requireNotNull(node.application.id) { "Application must have ID" }

                // Find edges pointing TO this integration node (methods calling it)
                val incomingEdges = edgeRepo.findAllByDstId(nodeId)

                incomingEdges.forEach { edge ->
                    val edgeKind = edge.kind.name
                    val edgeKey = "$appId:$integrationId:$edgeKind"

                    // Aggregate edges by app+integration+kind
                    edgeCountMap[edgeKey] = (edgeCountMap[edgeKey] ?: 0) + 1
                }

                // Find edges FROM this integration node (for topics - consumers)
                val outgoingEdges = edgeRepo.findAllBySrcId(nodeId)

                outgoingEdges.forEach { edge ->
                    val edgeKind = edge.kind.name
                    val edgeKey = "$appId:$integrationId:$edgeKind"

                    edgeCountMap[edgeKey] = (edgeCountMap[edgeKey] ?: 0) + 1
                }
            }
        }

        // 7. Create aggregated edges
        edgeCountMap.forEach { (key, count) ->
            val parts = key.split(":")
            val appId = parts[0]
            val integrationId = "${parts[1]}:${parts[2]}:${parts.drop(3).joinToString(":")}"
            val edgeKind = parts.last()

            crossAppEdges.add(
                CrossAppEdge(
                    source = "app:$appId",
                    target = integrationId,
                    kind = edgeKind,
                    methodCount = count
                )
            )
        }

        val statistics = CrossAppStatistics(
            applicationCount = apps.size,
            httpEndpoints = httpCount,
            kafkaTopics = kafkaCount,
            camelRoutes = camelCount,
            totalEdges = crossAppEdges.size
        )

        log.info("Cross-app graph built: {} nodes, {} edges", crossAppNodes.size, crossAppEdges.size)

        return CrossAppGraphResponse(
            nodes = crossAppNodes,
            edges = crossAppEdges,
            statistics = statistics
        )
    }

    /**
     * Проверяет, соответствует ли FQN интеграционного узла указанным типам.
     */
    private fun matchesIntegrationType(fqn: String, types: Set<IntegrationType>): Boolean {
        return types.any { type ->
            when (type) {
                IntegrationType.HTTP -> fqn.startsWith("infra:http:")
                IntegrationType.KAFKA -> fqn.startsWith("infra:kafka:")
                IntegrationType.CAMEL -> fqn.startsWith("infra:camel:")
            }
        }
    }

    /**
     * Извлекает читаемую метку для интеграционной точки из FQN.
     */
    private fun extractIntegrationLabel(fqn: String, nodeName: String?): String {
        // Если есть name, используем его
        if (!nodeName.isNullOrBlank() && nodeName != fqn) {
            return nodeName
        }

        // Парсим FQN
        return when {
            fqn.startsWith("infra:http:") -> {
                // infra:http:GET:https://api.example.com/users -> "GET /users"
                val parts = fqn.removePrefix("infra:http:").split(":", limit = 2)
                if (parts.size == 2) {
                    val method = parts[0]
                    val url = parts[1]
                    val path = url.substringAfter("//").substringAfter("/").let {
                        if (it.isBlank()) "/" else "/$it"
                    }
                    "$method $path"
                } else {
                    fqn
                }
            }
            fqn.startsWith("infra:kafka:topic:") -> {
                // infra:kafka:topic:user-events -> "user-events"
                fqn.removePrefix("infra:kafka:topic:")
            }
            fqn.startsWith("infra:camel:uri:") -> {
                // infra:camel:uri:direct:processOrder -> "direct:processOrder"
                fqn.removePrefix("infra:camel:uri:")
            }
            else -> fqn
        }
    }
}
