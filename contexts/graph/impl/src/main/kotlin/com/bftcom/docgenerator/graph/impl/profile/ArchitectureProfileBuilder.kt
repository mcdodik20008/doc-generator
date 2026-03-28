package com.bftcom.docgenerator.graph.impl.profile

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

/**
 * Строит текстовый архитектурный профиль приложения на основе данных графа.
 * Профиль сохраняется как Chunk и становится доступен для векторного поиска.
 */
@Component
class ArchitectureProfileBuilder(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
    private val chunkRepo: ChunkRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val LARGE_PAGE = PageRequest.of(0, 10_000)
    }

    /**
     * Агрегирует данные графа приложения в структурированный Markdown-профиль.
     */
    fun buildProfile(application: Application): String {
        val appId = application.id ?: return ""

        val allNodes = nodeRepo.findAllByApplicationId(appId, LARGE_PAGE)
        if (allNodes.isEmpty()) return ""

        val nodesByKind = allNodes.groupBy { it.kind }

        return buildString {
            appendLine("# Архитектурный профиль: ${application.key}")
            appendLine()

            appendLayerCounts(nodesByKind)
            appendApiSurface(nodesByKind[NodeKind.ENDPOINT].orEmpty())
            appendIntegrationMap(nodesByKind)
            appendDataModel(nodesByKind, appId)
            appendCrossCuttingPatterns(allNodes, appId)
            appendModuleStructure(nodesByKind[NodeKind.MODULE].orEmpty())
        }.trim()
    }

    /**
     * Сохраняет профиль как Chunk, привязанный к REPO-ноде приложения.
     */
    fun persistAsChunk(application: Application, profileText: String) {
        val appId = application.id ?: return
        if (profileText.isBlank()) return

        val repoNode = nodeRepo.findAllByApplicationIdAndKindIn(
            appId, setOf(NodeKind.REPO), PageRequest.of(0, 1)
        ).firstOrNull()

        if (repoNode == null) {
            log.warn("No REPO node found for application {}, skipping profile persistence", appId)
            return
        }

        val nodeId = repoNode.id ?: return
        val metadataJson = objectMapper.writeValueAsString(mapOf("profileType" to "architecture"))

        chunkRepo.upsertDocChunk(
            applicationId = appId,
            nodeId = nodeId,
            locale = "ru",
            kind = "arch_profile",
            content = profileText,
            metadataJson = metadataJson,
        )

        log.info("Architecture profile persisted for application {} ({} chars)", appId, profileText.length)
    }

    private fun StringBuilder.appendLayerCounts(nodesByKind: Map<NodeKind, List<Node>>) {
        appendLine("## Состав системы")
        appendLine()

        val relevantKinds = listOf(
            NodeKind.MODULE, NodeKind.PACKAGE, NodeKind.CLASS, NodeKind.INTERFACE,
            NodeKind.METHOD, NodeKind.ENDPOINT, NodeKind.SERVICE, NodeKind.CLIENT,
            NodeKind.TOPIC, NodeKind.JOB, NodeKind.DB_TABLE, NodeKind.DB_VIEW,
            NodeKind.INFRASTRUCTURE, NodeKind.CONFIG, NodeKind.EXCEPTION,
            NodeKind.MAPPER, NodeKind.TEST, NodeKind.MIGRATION,
        )

        for (kind in relevantKinds) {
            val count = nodesByKind[kind]?.size ?: 0
            if (count > 0) {
                appendLine("- $kind: $count")
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendApiSurface(endpoints: List<Node>) {
        if (endpoints.isEmpty()) return

        appendLine("## API (эндпоинты)")
        appendLine()
        for (ep in endpoints.take(50)) {
            val method = ep.meta["httpMethod"] ?: ""
            val path = ep.meta["path"] ?: ep.meta["url"] ?: ""
            val label = listOfNotNull(method.toString().takeIf { it.isNotBlank() }, path.toString().takeIf { it.isNotBlank() })
                .joinToString(" ")
                .ifBlank { ep.name ?: ep.fqn }
            appendLine("- $label")
        }
        if (endpoints.size > 50) {
            appendLine("- ... и ещё ${endpoints.size - 50} эндпоинтов")
        }
        appendLine()
    }

    private fun StringBuilder.appendIntegrationMap(nodesByKind: Map<NodeKind, List<Node>>) {
        val infraNodes = nodesByKind[NodeKind.INFRASTRUCTURE].orEmpty()
        val clientNodes = nodesByKind[NodeKind.CLIENT].orEmpty()
        val topicNodes = nodesByKind[NodeKind.TOPIC].orEmpty()

        if (infraNodes.isEmpty() && clientNodes.isEmpty() && topicNodes.isEmpty()) return

        appendLine("## Интеграции")
        appendLine()

        if (infraNodes.isNotEmpty()) {
            val byType = infraNodes.groupBy { (it.meta["integrationType"] as? String) ?: "unknown" }
            for ((type, nodes) in byType) {
                appendLine("### $type")
                for (node in nodes.take(20)) {
                    appendLine("- ${node.name ?: node.fqn}")
                }
                if (nodes.size > 20) appendLine("- ... и ещё ${nodes.size - 20}")
            }
            appendLine()
        }

        if (clientNodes.isNotEmpty()) {
            appendLine("### HTTP-клиенты")
            for (client in clientNodes.take(20)) {
                appendLine("- ${client.name ?: client.fqn}")
            }
            if (clientNodes.size > 20) appendLine("- ... и ещё ${clientNodes.size - 20}")
            appendLine()
        }

        if (topicNodes.isNotEmpty()) {
            appendLine("### Топики/очереди")
            for (topic in topicNodes.take(20)) {
                appendLine("- ${topic.name ?: topic.fqn}")
            }
            if (topicNodes.size > 20) appendLine("- ... и ещё ${topicNodes.size - 20}")
            appendLine()
        }
    }

    private fun StringBuilder.appendDataModel(nodesByKind: Map<NodeKind, List<Node>>, appId: Long) {
        val tables = nodesByKind[NodeKind.DB_TABLE].orEmpty()
        val views = nodesByKind[NodeKind.DB_VIEW].orEmpty()
        if (tables.isEmpty() && views.isEmpty()) return

        appendLine("## Модель данных")
        appendLine()

        val allDataNodes = tables + views
        val dataNodeIds = allDataNodes.mapNotNull { it.id }.toSet()

        // Находим кто читает/пишет в таблицы
        val edges = if (dataNodeIds.isNotEmpty()) {
            edgeRepo.findAllByDstIdIn(dataNodeIds)
                .filter { it.kind in setOf(EdgeKind.READS, EdgeKind.WRITES) }
        } else {
            emptyList()
        }

        val accessMap = edges.groupBy { it.dst.id }

        for (dataNode in allDataNodes.take(30)) {
            val nodeId = dataNode.id ?: continue
            val accessors = accessMap[nodeId].orEmpty()
            val readerNames = accessors.filter { it.kind == EdgeKind.READS }.map { it.src.name ?: it.src.fqn }
            val writerNames = accessors.filter { it.kind == EdgeKind.WRITES }.map { it.src.name ?: it.src.fqn }

            append("- ${dataNode.kind}: ${dataNode.name ?: dataNode.fqn}")
            val details = mutableListOf<String>()
            if (readerNames.isNotEmpty()) details.add("читают: ${readerNames.take(5).joinToString(", ")}")
            if (writerNames.isNotEmpty()) details.add("пишут: ${writerNames.take(5).joinToString(", ")}")
            if (details.isNotEmpty()) append(" (${details.joinToString("; ")})")
            appendLine()
        }
        if (allDataNodes.size > 30) {
            appendLine("- ... и ещё ${allDataNodes.size - 30} объектов данных")
        }
        appendLine()
    }

    private fun StringBuilder.appendCrossCuttingPatterns(allNodes: List<Node>, appId: Long) {
        val nodeIds = allNodes.mapNotNull { it.id }.toSet()
        if (nodeIds.isEmpty()) return

        val annotationEdges = edgeRepo.findAllBySrcIdIn(nodeIds)
            .filter { it.kind == EdgeKind.ANNOTATED_WITH }

        if (annotationEdges.isEmpty()) return

        val interestingAnnotations = setOf(
            "PreAuthorize", "Secured", "RolesAllowed",
            "Transactional",
            "Async",
            "Cacheable", "CacheEvict", "CachePut",
            "Scheduled", "Retryable", "CircuitBreaker",
        )

        val grouped = annotationEdges
            .groupBy { it.dst.name ?: it.dst.fqn.substringAfterLast('.') }
            .filter { (name, _) -> interestingAnnotations.any { ann -> name.contains(ann, ignoreCase = true) } }

        if (grouped.isEmpty()) return

        appendLine("## Сквозная функциональность (аннотации)")
        appendLine()
        for ((annotation, edges) in grouped.entries.sortedByDescending { it.value.size }) {
            appendLine("- @$annotation: ${edges.size} использований")
        }
        appendLine()
    }

    private fun StringBuilder.appendModuleStructure(modules: List<Node>) {
        if (modules.isEmpty()) return

        appendLine("## Модули")
        appendLine()
        for (module in modules.take(20)) {
            appendLine("- ${module.name ?: module.fqn}")
        }
        if (modules.size > 20) {
            appendLine("- ... и ещё ${modules.size - 20} модулей")
        }
        appendLine()
    }
}
