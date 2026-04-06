package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.impl.pathfinding.GraphPathFinder
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

/**
 * Шаг синтеза архитектурного контекста.
 * Формирует структурированную информацию об архитектуре приложения
 * на основе целевых запросов к графу.
 */
@Component
class ArchitectureSynthesisStep(
    private val nodeRepository: NodeRepository,
    private val edgeRepository: EdgeRepository,
    private val graphPathFinder: GraphPathFinder,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.ARCHITECTURE_SYNTHESIS

    companion object {
        private val LARGE_PAGE = PageRequest.of(0, 500)

        private val SECURITY_KEYWORDS = listOf("авториз", "безопасност", "security", "auth", "доступ", "права")
        private val INTEGRATION_KEYWORDS = listOf("интеграц", "внешни", "integration", "клиент", "client", "http", "kafka", "rabbit")
        private val DATA_KEYWORDS = listOf("данн", "модел", "табли", "data", "model", "table", "бд", "database", "хранени")
    }

    override fun execute(context: QueryProcessingContext): StepResult {
        val appId = context.getMetadata<Long>(QueryMetadataKeys.APPLICATION_ID)
        val query = context.currentQuery.lowercase()

        val archText = buildString {
            if (appId != null) {
                val topic = detectTopic(query)
                when (topic) {
                    Topic.SECURITY -> appendSecurityContext(appId)
                    Topic.INTEGRATION -> appendIntegrationContext(appId)
                    Topic.DATA -> appendDataContext(appId)
                    Topic.GENERAL -> appendGeneralContext(appId)
                }
            } else {
                append("Не указан applicationId — невозможно собрать архитектурный контекст.")
            }
        }

        val exactNodes = if (appId != null) {
            collectArchitectureNodes(appId, detectTopic(query))
        } else {
            emptyList()
        }

        // Find paths between architecture nodes
        val archTextWithPaths = if (exactNodes.size >= 2) {
            val nodeIds = exactNodes.mapNotNull { it.id }
            val paths = graphPathFinder.findPathsBetweenNodes(nodeIds)
            if (paths.isNotEmpty()) {
                archText + "\n\n" + graphPathFinder.formatPaths(paths)
            } else archText
        } else archText

        val updatedContext = context
            .setMetadata(QueryMetadataKeys.ARCHITECTURE_CONTEXT_TEXT, archTextWithPaths)
            .apply {
                if (exactNodes.isNotEmpty()) {
                    setMetadata(QueryMetadataKeys.EXACT_NODES, exactNodes)
                }
            }
            .addStep(
                ProcessingStep(
                    advisorName = "ArchitectureSynthesisStep",
                    input = context.currentQuery,
                    output = "Архитектурный контекст: ${archTextWithPaths.length} символов, узлов: ${exactNodes.size}",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )

        log.info("ARCHITECTURE_SYNTHESIS: context_length={}, nodes={}", archTextWithPaths.length, exactNodes.size)
        return StepResult(context = updatedContext, transitionKey = "SUCCESS")
    }

    private enum class Topic { GENERAL, SECURITY, INTEGRATION, DATA }

    private fun detectTopic(query: String): Topic {
        return when {
            SECURITY_KEYWORDS.any { query.contains(it) } -> Topic.SECURITY
            INTEGRATION_KEYWORDS.any { query.contains(it) } -> Topic.INTEGRATION
            DATA_KEYWORDS.any { query.contains(it) } -> Topic.DATA
            else -> Topic.GENERAL
        }
    }

    private fun StringBuilder.appendGeneralContext(appId: Long) {
        val kindCounts = countNodesByKind(appId)
        appendLine("## Общая архитектура")
        appendLine()
        if (kindCounts.isNotEmpty()) {
            appendLine("Состав системы:")
            for ((kind, count) in kindCounts) {
                appendLine("- $kind: $count")
            }
            appendLine()
        }

        val endpoints = loadNodes(appId, setOf(NodeKind.ENDPOINT), 20)
        if (endpoints.isNotEmpty()) {
            appendLine("API эндпоинты:")
            for (ep in endpoints) {
                val method = ep.meta["httpMethod"] ?: ""
                val path = ep.meta["path"] ?: ""
                appendLine("- $method $path (${ep.name ?: ep.fqn})")
            }
            appendLine()
        }

        val modules = loadNodes(appId, setOf(NodeKind.MODULE), 20)
        if (modules.isNotEmpty()) {
            appendLine("Модули:")
            for (m in modules) {
                appendLine("- ${m.name ?: m.fqn}")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendSecurityContext(appId: Long) {
        appendLine("## Безопасность и авторизация")
        appendLine()

        // Ищем INFRASTRUCTURE с типом auth/oauth
        val infraNodes = loadNodes(appId, setOf(NodeKind.INFRASTRUCTURE), 100)
        val authInfra = infraNodes.filter {
            val type = (it.meta["integrationType"] as? String)?.lowercase() ?: ""
            type.contains("auth") || type.contains("oauth") || type.contains("security")
        }
        if (authInfra.isNotEmpty()) {
            appendLine("Инфраструктура безопасности:")
            for (node in authInfra) {
                appendLine("- ${node.name ?: node.fqn} (${node.meta["integrationType"]})")
            }
            appendLine()
        }

        // Ищем аннотации @PreAuthorize, @Secured
        val allNodeIds = nodeRepository.findAllByApplicationId(appId, LARGE_PAGE).mapNotNull { it.id }.toSet()
        if (allNodeIds.isNotEmpty()) {
            val annotationEdges = edgeRepository.findAllBySrcIdIn(allNodeIds)
                .filter { it.kind == EdgeKind.ANNOTATED_WITH }
                .filter {
                    val dstName = it.dst.name ?: it.dst.fqn
                    dstName.contains("PreAuthorize", true) ||
                        dstName.contains("Secured", true) ||
                        dstName.contains("RolesAllowed", true)
                }
            if (annotationEdges.isNotEmpty()) {
                appendLine("Защищённые методы (${annotationEdges.size} шт.):")
                for (edge in annotationEdges.take(15)) {
                    appendLine("- ${edge.src.name ?: edge.src.fqn} → @${edge.dst.name ?: edge.dst.fqn.substringAfterLast('.')}")
                }
                appendLine()
            }
        }
    }

    private fun StringBuilder.appendIntegrationContext(appId: Long) {
        appendLine("## Интеграции")
        appendLine()

        val clients = loadNodes(appId, setOf(NodeKind.CLIENT), 30)
        if (clients.isNotEmpty()) {
            appendLine("HTTP-клиенты:")
            for (c in clients) {
                appendLine("- ${c.name ?: c.fqn}")
            }
            appendLine()
        }

        val infra = loadNodes(appId, setOf(NodeKind.INFRASTRUCTURE), 50)
        if (infra.isNotEmpty()) {
            val byType = infra.groupBy { (it.meta["integrationType"] as? String) ?: "unknown" }
            for ((type, nodes) in byType) {
                appendLine("$type:")
                for (n in nodes.take(10)) {
                    appendLine("- ${n.name ?: n.fqn}")
                }
                appendLine()
            }
        }

        val topics = loadNodes(appId, setOf(NodeKind.TOPIC), 30)
        if (topics.isNotEmpty()) {
            appendLine("Топики/очереди:")
            for (t in topics) {
                appendLine("- ${t.name ?: t.fqn}")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendDataContext(appId: Long) {
        appendLine("## Модель данных")
        appendLine()

        val tables = loadNodes(appId, setOf(NodeKind.DB_TABLE, NodeKind.DB_VIEW), 50)
        if (tables.isEmpty()) {
            appendLine("Таблицы и представления не найдены.")
            return
        }

        val tableIds = tables.mapNotNull { it.id }.toSet()
        val edges = edgeRepository.findAllByDstIdIn(tableIds)
            .filter { it.kind in setOf(EdgeKind.READS, EdgeKind.WRITES) }
        val accessMap = edges.groupBy { it.dst.id }

        for (table in tables) {
            val nodeId = table.id ?: continue
            val accessors = accessMap[nodeId].orEmpty()
            val readers = accessors.filter { it.kind == EdgeKind.READS }.map { it.src.name ?: it.src.fqn }
            val writers = accessors.filter { it.kind == EdgeKind.WRITES }.map { it.src.name ?: it.src.fqn }

            append("- ${table.kind}: ${table.name ?: table.fqn}")
            val details = mutableListOf<String>()
            if (readers.isNotEmpty()) details.add("читают: ${readers.take(5).joinToString(", ")}")
            if (writers.isNotEmpty()) details.add("пишут: ${writers.take(5).joinToString(", ")}")
            if (details.isNotEmpty()) append(" (${details.joinToString("; ")})")
            appendLine()
        }
    }

    private fun countNodesByKind(appId: Long): Map<NodeKind, Int> {
        val nodes = nodeRepository.findAllByApplicationId(appId, LARGE_PAGE)
        return nodes.groupBy { it.kind }.mapValues { it.value.size }
            .toSortedMap()
    }

    private fun loadNodes(appId: Long, kinds: Set<NodeKind>, limit: Int): List<Node> {
        return nodeRepository.findAllByApplicationIdAndKindIn(appId, kinds, PageRequest.of(0, limit))
    }

    private fun collectArchitectureNodes(appId: Long, topic: Topic): List<Node> {
        val kinds = when (topic) {
            Topic.SECURITY -> setOf(NodeKind.INFRASTRUCTURE, NodeKind.CONFIG)
            Topic.INTEGRATION -> setOf(NodeKind.CLIENT, NodeKind.INFRASTRUCTURE, NodeKind.TOPIC)
            Topic.DATA -> setOf(NodeKind.DB_TABLE, NodeKind.DB_VIEW)
            Topic.GENERAL -> setOf(NodeKind.ENDPOINT, NodeKind.MODULE, NodeKind.INFRASTRUCTURE)
        }
        return loadNodes(appId, kinds, 10)
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.VECTOR_SEARCH,
        )
    }
}
