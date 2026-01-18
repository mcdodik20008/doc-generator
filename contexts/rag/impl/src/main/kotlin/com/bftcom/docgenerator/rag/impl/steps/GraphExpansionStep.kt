package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GraphExpansionStep(
    private val edgeRepository: EdgeRepository,
    private val nodeRepository: NodeRepository,
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

        val radius = (context.getMetadata<Int>(QueryMetadataKeys.NEIGHBOR_EXPANSION_RADIUS) ?: 1)
            .coerceIn(1, 2)

        val seedIds = exactNodes.mapNotNull { it.id }.toSet()
        val visitedIds = seedIds.toMutableSet()
        var frontier = seedIds
        val collectedEdges = mutableListOf<Edge>()

        repeat(radius) {
            if (frontier.isEmpty()) return@repeat
            val edges = findRelevantEdges(frontier)
            collectedEdges.addAll(edges)
            val newIds = edges.flatMap { listOfNotNull(it.src.id, it.dst.id) }.toSet()
            val nextFrontier = newIds - visitedIds
            visitedIds.addAll(newIds)
            frontier = nextFrontier
        }

        val neighborIds = (visitedIds - seedIds)
        val neighborNodes = if (neighborIds.isNotEmpty()) {
            nodeRepository.findAllByIdIn(neighborIds)
        } else {
            emptyList()
        }

        val graphText = buildGraphRelationsText(collectedEdges, exactNodes, neighborNodes)
        val updatedContext = context
            .setMetadata(QueryMetadataKeys.NEIGHBOR_NODES, neighborNodes)
            .setMetadata(QueryMetadataKeys.GRAPH_RELATIONS_TEXT, graphText)
            .addStep(
                ProcessingStep(
                    advisorName = "GraphExpansionStep",
                    input = context.currentQuery,
                    output = "Ребер: ${collectedEdges.size}, соседних узлов: ${neighborNodes.size}",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )

        log.info("GRAPH_EXPANSION: edges={}, neighbors={}", collectedEdges.size, neighborNodes.size)
        return StepResult(
            context = updatedContext,
            transitionKey = "SUCCESS",
        )
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
            !node.name.isNullOrBlank() -> node.name!!
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
            else -> "связан с"
        }
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.RERANKING,
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
        )
    }
}
