package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.db.findAllByIdInBatched
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.bftcom.docgenerator.rag.impl.pathfinding.GraphPathFinder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GraphExpansionStep(
    private val edgeRepository: EdgeRepository,
    private val nodeRepository: NodeRepository,
    private val graphPathFinder: GraphPathFinder,
    @Value("\${docgen.rag.graph-expansion.radius.default:1}")
    private val defaultRadius: Int = 1,
    @Value("\${docgen.rag.graph-expansion.radius.architecture:2}")
    private val architectureRadius: Int = 2,
    @Value("\${docgen.rag.graph-expansion.radius.stacktrace:2}")
    private val stacktraceRadius: Int = 2,
    @Value("\${docgen.rag.graph-expansion.radius.max:3}")
    private val maxRadius: Int = 3,
    @Value("\${docgen.rag.graph-expansion.max-neighbors-per-hop:50}")
    private val maxNeighborsPerHop: Int = 50,
) : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.GRAPH_EXPANSION

    override fun execute(context: QueryProcessingContext): StepResult {
        val exactNodes = context.getMetadata<List<*>>(QueryMetadataKeys.EXACT_NODES)
            ?.filterIsInstance<Node>()
            .orEmpty()

        if (exactNodes.isEmpty()) {
            val updatedContext = context.addStep(
                ProcessingStep(
                    advisorName = "GraphExpansionStep",
                    input = context.currentQuery,
                    output = "Нет точных узлов для расширения, переходим к VECTOR_SEARCH",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )
            return StepResult(
                context = updatedContext,
                transitionKey = "NO_NODES",
            )
        }

        // Intent-aware radius selection
        val intent = context.getMetadata<String>(QueryMetadataKeys.QUERY_INTENT)
        val explicitRadius = context.getMetadata<Int>(QueryMetadataKeys.NEIGHBOR_EXPANSION_RADIUS)
        val radius = (explicitRadius ?: selectRadiusByIntent(intent)).coerceIn(1, maxRadius)

        val seedIds = exactNodes.mapNotNull { it.id }.toSet()
        val visitedIds = seedIds.toMutableSet()
        var frontier = seedIds
        val collectedEdges = mutableListOf<Edge>()

        repeat(radius) {
            if (frontier.isEmpty()) return@repeat
            val edges = findRelevantEdges(frontier)
            collectedEdges.addAll(edges)

            // Collect neighbor IDs, cap at maxNeighborsPerHop sorted by edge weight
            val neighborEdges = edges.flatMap { edge ->
                val w = EDGE_WEIGHTS[edge.kind] ?: 0.5
                listOfNotNull(edge.src.id?.let { it to w }, edge.dst.id?.let { it to w })
            }
            val newIds = neighborEdges
                .filter { (id, _) -> id !in visitedIds }
                .sortedByDescending { (_, weight) -> weight }
                .take(maxNeighborsPerHop)
                .map { (id, _) -> id }
                .toSet()

            visitedIds.addAll(newIds)
            frontier = newIds
        }

        val neighborIds = (visitedIds - seedIds)
        val neighborNodes = if (neighborIds.isNotEmpty()) {
            nodeRepository.findAllByIdInBatched(neighborIds)
        } else {
            emptyList()
        }

        var graphText = buildGraphRelationsText(collectedEdges, exactNodes, neighborNodes)

        // For ARCHITECTURE intent, find shortest paths between exact nodes
        if (intent?.uppercase() == "ARCHITECTURE" && seedIds.size >= 2) {
            val paths = graphPathFinder.findPathsBetweenNodes(seedIds.toList())
            if (paths.isNotEmpty()) {
                val pathsText = graphPathFinder.formatPaths(paths)
                graphText = if (graphText.isNotBlank()) "$graphText\n\n$pathsText" else pathsText
            }
        }

        val updatedContext = context
            .setMetadata(QueryMetadataKeys.NEIGHBOR_NODES, neighborNodes)
            .setMetadata(QueryMetadataKeys.GRAPH_RELATIONS_TEXT, graphText)
            .addStep(
                ProcessingStep(
                    advisorName = "GraphExpansionStep",
                    input = context.currentQuery,
                    output = "Ребер: ${collectedEdges.size}, соседних узлов: ${neighborNodes.size}, radius: $radius, intent: $intent",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )

        log.info("GRAPH_EXPANSION: edges={}, neighbors={}, radius={}, intent={}", collectedEdges.size, neighborNodes.size, radius, intent)
        return StepResult(
            context = updatedContext,
            transitionKey = "SUCCESS",
        )
    }

    private fun selectRadiusByIntent(intent: String?): Int = when (intent?.uppercase()) {
        "ARCHITECTURE" -> architectureRadius
        "STACKTRACE" -> stacktraceRadius
        else -> defaultRadius
    }

    private fun findRelevantEdges(nodeIds: Set<Long>): List<Edge> {
        val outgoing = edgeRepository.findAllBySrcIdIn(nodeIds)
        val incoming = edgeRepository.findAllByDstIdIn(nodeIds)
        return (outgoing + incoming)
            .filter { it.kind in ALLOWED_EDGE_KINDS }
            .distinctBy { edge -> "${edge.src.id}:${edge.dst.id}:${edge.kind.name}" }
    }

    private fun buildGraphRelationsText(
        edges: List<Edge>,
        exactNodes: List<Node>,
        neighborNodes: List<Node>,
    ): String {
        if (edges.isEmpty()) {
            return ""
        }

        val allNodes = (exactNodes + neighborNodes).associateBy { it.id }
        val lines = edges.mapNotNull { edge ->
            val srcId = edge.src.id
            val dstId = edge.dst.id
            val srcLabel = nodeLabel(allNodes[srcId], srcId)
            val dstLabel = nodeLabel(allNodes[dstId], dstId)
            if (srcLabel.isBlank() || dstLabel.isBlank()) {
                null
            } else {
                "- [$srcLabel] ${relationVerb(edge.kind)} [$dstLabel] (Тип: ${edge.kind})"
            }
        }

        if (lines.isEmpty()) {
            return ""
        }

        return buildString {
            append("СВЯЗИ В ГРАФЕ КОДА:\n")
            append(lines.joinToString("\n"))
        }
    }

    private fun nodeLabel(node: Node?, fallbackId: Long?): String {
        if (node == null) {
            return fallbackId?.let { "Node#$it" } ?: ""
        }
        return when {
            !node.name.isNullOrBlank() -> node.name.orEmpty()
            node.fqn.isNotBlank() -> node.fqn
            node.id != null -> "Node#${node.id}"
            fallbackId != null -> "Node#$fallbackId"
            else -> ""
        }
    }

    private fun relationVerb(kind: EdgeKind): String {
        return when (kind) {
            EdgeKind.CALLS_CODE -> "вызывает"
            EdgeKind.DEPENDS_ON -> "зависит от"
            EdgeKind.IMPLEMENTS -> "реализует"
            EdgeKind.READS -> "читает из"
            EdgeKind.WRITES -> "пишет в"
            EdgeKind.CALLS_HTTP -> "вызывает HTTP"
            EdgeKind.PRODUCES -> "публикует в"
            EdgeKind.CONSUMES -> "потребляет из"
            EdgeKind.CONTAINS -> "содержит"
            else -> "связан с"
        }
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.VECTOR_SEARCH,
            "NO_NODES" to ProcessingStepType.VECTOR_SEARCH,
        )
    }

    companion object {
        private val ALLOWED_EDGE_KINDS = setOf(
            EdgeKind.CALLS_CODE,
            EdgeKind.DEPENDS_ON,
            EdgeKind.IMPLEMENTS,
            EdgeKind.READS,
            EdgeKind.WRITES,
            EdgeKind.CALLS_HTTP,
            EdgeKind.PRODUCES,
            EdgeKind.CONSUMES,
            EdgeKind.CONTAINS,
        )

        /** Веса рёбер для приоритизации соседей при BFS. */
        val EDGE_WEIGHTS: Map<EdgeKind, Double> = mapOf(
            EdgeKind.CALLS_CODE to 1.0,
            EdgeKind.IMPLEMENTS to 0.8,
            EdgeKind.DEPENDS_ON to 0.7,
            EdgeKind.CALLS_HTTP to 0.7,
            EdgeKind.PRODUCES to 0.6,
            EdgeKind.CONSUMES to 0.6,
            EdgeKind.READS to 0.5,
            EdgeKind.WRITES to 0.5,
            EdgeKind.CONTAINS to 0.5,
        )
    }
}
