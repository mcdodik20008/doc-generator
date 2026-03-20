package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.dto.*
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.CrossAppGraphService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Реализация сервиса для построения cross-app графов.
 *
 * Использует две стратегии поиска интеграционных связей:
 * - Strategy A: API Contract Matching — обнаружение shared интерфейсов (API контрактов) между приложениями
 * - Strategy B: Synthetic Node Matching — поиск синтетических нод (meta.synthetic=true)
 *
 * Результаты обеих стратегий объединяются с дедупликацией.
 */
@Service
@Transactional(readOnly = true)
class CrossAppGraphServiceImpl(
    private val applicationRepo: ApplicationRepository,
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
) : CrossAppGraphService {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Внутреннее представление результата стратегии (без дублирования логики). */
    private data class IntegrationResult(
        val nodes: List<CrossAppNode>,
        val edges: List<CrossAppEdge>,
        val httpCount: Int = 0,
        val kafkaCount: Int = 0,
        val camelCount: Int = 0,
        val apiContractCount: Int = 0,
    ) {
        companion object {
            val EMPTY = IntegrationResult(emptyList(), emptyList())
        }
    }

    /** Ключ агрегации рёбер — заменяет хрупкий split(":"). */
    private data class AggregationKey(val appId: Long, val integrationId: String, val edgeKind: String)

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

        // 2. Run both strategies
        val strategyA = findApiContracts(appIds, integrationTypes)
        val strategyB = findSyntheticNodes(appIds, integrationTypes, limit)

        // 3. Build application nodes
        val appNodes = apps.map { app ->
            CrossAppNode(
                id = "app:${app.id}",
                label = app.name ?: app.key,
                kind = "APPLICATION",
                metadata = mapOf(
                    "key" to app.key,
                    "description" to (app.description ?: "")
                )
            )
        }

        // 4. Merge results with deduplication
        val allIntegrationNodes = mutableMapOf<String, CrossAppNode>()
        val allEdges = mutableMapOf<String, CrossAppEdge>()

        for (result in listOf(strategyA, strategyB)) {
            for (node in result.nodes) {
                allIntegrationNodes.putIfAbsent(node.id, node)
            }
            for (edge in result.edges) {
                val edgeKey = "${edge.source}_${edge.target}_${edge.kind}"
                allEdges.merge(edgeKey, edge) { existing, new ->
                    existing.copy(methodCount = existing.methodCount + new.methodCount)
                }
            }
        }

        val crossAppNodes = appNodes + allIntegrationNodes.values.toList()
        val crossAppEdges = allEdges.values.toList()

        val statistics = CrossAppStatistics(
            applicationCount = apps.size,
            httpEndpoints = strategyB.httpCount,
            kafkaTopics = strategyB.kafkaCount,
            camelRoutes = strategyB.camelCount,
            totalEdges = crossAppEdges.size,
            apiContracts = strategyA.apiContractCount
        )

        log.info(
            "Cross-app graph built: {} nodes, {} edges (apiContracts={}, synthetic={})",
            crossAppNodes.size, crossAppEdges.size,
            strategyA.apiContractCount,
            strategyB.nodes.size
        )

        return CrossAppGraphResponse(
            nodes = crossAppNodes,
            edges = crossAppEdges,
            statistics = statistics
        )
    }

    // =====================================================================
    //  Strategy A: API Contract Matching
    // =====================================================================

    /**
     * Находит shared API контракты — INTERFACE ноды с одинаковым FQN в нескольких приложениях.
     * Для каждого такого интерфейса определяет provider (кто реализует) и consumer (кто использует).
     */
    private fun findApiContracts(
        appIds: List<Long>,
        integrationTypes: Set<IntegrationType>,
    ): IntegrationResult {
        // API contracts — это HTTP-based, пропускаем если запрошены только KAFKA/CAMEL
        if (integrationTypes.isNotEmpty() && IntegrationType.HTTP !in integrationTypes) {
            return IntegrationResult.EMPTY
        }

        // Загружаем все INTERFACE ноды по выбранным приложениям
        val interfaceNodes = nodeRepo.findAllByApplicationIdInAndKindIn(
            appIds,
            setOf(NodeKind.INTERFACE)
        )

        if (interfaceNodes.isEmpty()) {
            log.debug("No INTERFACE nodes found")
            return IntegrationResult.EMPTY
        }

        // Группируем по FQN — ищем интерфейсы, присутствующие в 2+ приложениях
        val sharedInterfaces = interfaceNodes.groupBy { it.fqn }
            .filter { (_, nodes) ->
                nodes.map { it.application.id }.distinct().size >= 2
            }

        if (sharedInterfaces.isEmpty()) {
            log.debug("No shared interfaces found across applications")
            return IntegrationResult.EMPTY
        }

        log.info("Found {} shared API contract interfaces", sharedInterfaces.size)

        // Загружаем class-ноды для определения Provider (кто реализует интерфейс)
        val classLikeNodes = nodeRepo.findAllByApplicationIdInAndKindIn(
            appIds,
            setOf(NodeKind.CLASS, NodeKind.ENDPOINT, NodeKind.SERVICE)
        )

        val nodes = mutableListOf<CrossAppNode>()
        val edges = mutableListOf<CrossAppEdge>()

        for ((interfaceFqn, ifaceNodes) in sharedInterfaces) {
            val simpleName = interfaceFqn.substringAfterLast('.')
            val integrationId = "contract:$interfaceFqn"

            // Определяем провайдеров: приложения, где есть класс, реализующий этот интерфейс
            val providerAppIds = mutableSetOf<Long>()
            for (classNode in classLikeNodes) {
                if (implementsInterface(classNode, interfaceFqn, simpleName)) {
                    val classAppId = classNode.application.id
                    if (classAppId != null) {
                        providerAppIds.add(classAppId)
                    }
                }
            }

            // Определяем consumers: приложения, у которых есть интерфейс, но нет реализации
            val allAppIdsWithInterface = ifaceNodes.mapNotNull { it.application.id }.toSet()
            val consumerAppIds = allAppIdsWithInterface - providerAppIds

            // Подсчитываем методы интерфейса
            val methodCount = ifaceNodes.maxOf { iface ->
                val ifaceId = iface.id ?: return@maxOf 0
                nodeRepo.findAllByParentId(ifaceId).count { it.kind == NodeKind.METHOD }
            }

            // Создаем узел API_CONTRACT
            nodes.add(
                CrossAppNode(
                    id = integrationId,
                    label = simpleName,
                    kind = "API_CONTRACT",
                    metadata = mapOf(
                        "fqn" to interfaceFqn,
                        "interfaceFqn" to interfaceFqn,
                        "methodCount" to methodCount,
                        "applicationsCount" to allAppIdsWithInterface.size,
                        "providers" to providerAppIds.toList(),
                        "consumers" to consumerAppIds.toList()
                    )
                )
            )

            // Создаем рёбра
            for (provId in providerAppIds) {
                edges.add(CrossAppEdge(source = "app:$provId", target = integrationId, kind = "PROVIDES"))
            }
            for (consId in consumerAppIds) {
                edges.add(CrossAppEdge(source = "app:$consId", target = integrationId, kind = "CONSUMES"))
            }
        }

        return IntegrationResult(
            nodes = nodes,
            edges = edges,
            apiContractCount = sharedInterfaces.size
        )
    }

    /**
     * Проверяет, реализует ли class-нода указанный интерфейс через supertypesSimple или supertypesResolved.
     */
    private fun implementsInterface(classNode: Node, interfaceFqn: String, simpleName: String): Boolean {
        val meta = classNode.meta
        val supertypesSimple = (meta["supertypesSimple"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val supertypesResolved = (meta["supertypesResolved"] as? List<*>)?.filterIsInstance<String>().orEmpty()

        return supertypesResolved.any { it == interfaceFqn } ||
            supertypesSimple.any { it == simpleName }
    }

    // =====================================================================
    //  Strategy B: Synthetic Node Matching (improved)
    // =====================================================================

    /**
     * Находит синтетические интеграционные ноды (meta.synthetic=true) и legacy-ноды VirtualNodeFactory.
     */
    private fun findSyntheticNodes(
        appIds: List<Long>,
        integrationTypes: Set<IntegrationType>,
        limit: Int,
    ): IntegrationResult {
        val integrationNodes = nodeRepo.findAllByApplicationIdInAndKindIn(
            appIds,
            setOf(NodeKind.ENDPOINT, NodeKind.TOPIC)
        ).filter { node ->
            val isSynthetic = node.meta["synthetic"] as? Boolean ?: false
            val isLegacyVirtual = node.meta["source"] == "library_analysis"
            if (!isSynthetic && !isLegacyVirtual) return@filter false

            if (integrationTypes.isEmpty()) return@filter true
            matchesIntegrationType(node.fqn, integrationTypes)
        }

        if (integrationNodes.isEmpty()) {
            return IntegrationResult.EMPTY
        }

        log.debug("Found {} synthetic integration nodes", integrationNodes.size)

        // Нормализуем FQN legacy-нод
        val normalizedGroups = integrationNodes.groupBy { normalizeFqn(it.fqn) }

        val nodes = mutableListOf<CrossAppNode>()
        val edgeCountMap = mutableMapOf<AggregationKey, Int>()
        var httpCount = 0
        var kafkaCount = 0
        var camelCount = 0

        normalizedGroups.entries.take(limit).forEach { (fqn, groupNodes) ->
            val representative = groupNodes.first()
            val integrationKind = representative.kind.name
            val integrationId = "integration:$integrationKind:$fqn"

            when {
                fqn.startsWith("infra:http:") -> httpCount++
                fqn.startsWith("infra:kafka:") -> kafkaCount++
                fqn.startsWith("infra:camel:") -> camelCount++
            }

            val label = extractIntegrationLabel(fqn, representative.name)
            nodes.add(
                CrossAppNode(
                    id = integrationId,
                    label = label,
                    kind = integrationKind,
                    metadata = mapOf(
                        "fqn" to fqn,
                        "applicationsCount" to groupNodes.map { it.application.id }.distinct().size
                    )
                )
            )

            groupNodes.forEach { node ->
                val nodeId = requireNotNull(node.id) { "Node must have ID" }
                val appId = requireNotNull(node.application.id) { "Application must have ID" }

                val incomingEdges = edgeRepo.findAllByDstId(nodeId)
                incomingEdges.forEach { edge ->
                    val key = AggregationKey(appId, integrationId, edge.kind.name)
                    edgeCountMap[key] = (edgeCountMap[key] ?: 0) + 1
                }

                val outgoingEdges = edgeRepo.findAllBySrcId(nodeId)
                outgoingEdges.forEach { edge ->
                    val key = AggregationKey(appId, integrationId, edge.kind.name)
                    edgeCountMap[key] = (edgeCountMap[key] ?: 0) + 1
                }
            }
        }

        val edges = edgeCountMap.map { (key, count) ->
            CrossAppEdge(
                source = "app:${key.appId}",
                target = key.integrationId,
                kind = key.edgeKind,
                methodCount = count
            )
        }

        return IntegrationResult(
            nodes = nodes,
            edges = edges,
            httpCount = httpCount,
            kafkaCount = kafkaCount,
            camelCount = camelCount
        )
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /**
     * Нормализует legacy FQN-форматы VirtualNodeFactory в стандартный формат infra:*.
     */
    private fun normalizeFqn(fqn: String): String = when {
        fqn.startsWith("endpoint://") -> {
            // "endpoint://GET https://..." -> "infra:http:GET:https://..."
            val rest = fqn.removePrefix("endpoint://")
            val spaceIdx = rest.indexOf(' ')
            if (spaceIdx > 0) {
                val method = rest.substring(0, spaceIdx)
                val url = rest.substring(spaceIdx + 1)
                "infra:http:$method:$url"
            } else {
                "infra:http:UNKNOWN:$rest"
            }
        }
        fqn.startsWith("topic://") -> {
            "infra:kafka:topic:${fqn.removePrefix("topic://")}"
        }
        else -> fqn
    }

    private fun matchesIntegrationType(fqn: String, types: Set<IntegrationType>): Boolean {
        val normalized = normalizeFqn(fqn)
        return types.any { type ->
            when (type) {
                IntegrationType.HTTP -> normalized.startsWith("infra:http:")
                IntegrationType.KAFKA -> normalized.startsWith("infra:kafka:")
                IntegrationType.CAMEL -> normalized.startsWith("infra:camel:")
            }
        }
    }

    private fun extractIntegrationLabel(fqn: String, nodeName: String?): String {
        if (!nodeName.isNullOrBlank() && nodeName != fqn) {
            return nodeName
        }

        return when {
            fqn.startsWith("infra:http:") -> {
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
            fqn.startsWith("infra:kafka:topic:") -> fqn.removePrefix("infra:kafka:topic:")
            fqn.startsWith("infra:camel:uri:") -> fqn.removePrefix("infra:camel:uri:")
            else -> fqn
        }
    }
}
