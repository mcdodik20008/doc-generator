package com.bftcom.docgenerator.rag.impl.pathfinding

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.db.findAllByIdInBatched
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.PriorityQueue

/**
 * Находит кратчайшие пути между узлами графа кода
 * с использованием алгоритма Dijkstra и весов рёбер.
 */
@Component
class GraphPathFinder(
    private val edgeRepository: EdgeRepository,
    private val nodeRepository: NodeRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /** Максимальная глубина поиска для защиты от OOM. */
        private const val MAX_DEPTH = 6

        /** Максимальное количество путей для возврата. */
        private const val MAX_PATHS = 5

        /** Веса рёбер по типу (меньше = ближе). */
        val EDGE_COSTS: Map<EdgeKind, Double> = mapOf(
            EdgeKind.CALLS_CODE to 1.0,
            EdgeKind.IMPLEMENTS to 1.2,
            EdgeKind.DEPENDS_ON to 1.5,
            EdgeKind.CALLS_HTTP to 1.5,
            EdgeKind.PRODUCES to 2.0,
            EdgeKind.CONSUMES to 2.0,
            EdgeKind.READS to 2.0,
            EdgeKind.WRITES to 2.0,
            EdgeKind.CONTAINS to 2.5,
        )

        private const val DEFAULT_COST = 3.0
    }

    /**
     * Находит кратчайший путь между двумя узлами с помощью Dijkstra.
     * Возвращает null если путь не найден в пределах MAX_DEPTH хопов.
     */
    fun findShortestPath(fromId: Long, toId: Long): GraphPath? {
        if (fromId == toId) {
            val node = nodeRepository.findById(fromId).orElse(null)
            return if (node != null) {
                GraphPath(nodes = listOf(node), edges = emptyList(), totalCost = 0.0)
            } else null
        }

        // Dijkstra
        val dist = mutableMapOf<Long, Double>()
        val prev = mutableMapOf<Long, Pair<Long, Edge>>() // nodeId -> (prevNodeId, edge)
        val visited = mutableSetOf<Long>()

        data class QueueEntry(val nodeId: Long, val cost: Double) : Comparable<QueueEntry> {
            override fun compareTo(other: QueueEntry): Int = cost.compareTo(other.cost)
        }

        val queue = PriorityQueue<QueueEntry>()
        dist[fromId] = 0.0
        queue.add(QueueEntry(fromId, 0.0))

        var hops = 0
        while (queue.isNotEmpty() && hops < MAX_DEPTH * 1000) {
            val (currentId, currentCost) = queue.poll()
            if (currentId in visited) continue
            visited.add(currentId)
            hops++

            if (currentId == toId) break

            // Check depth by counting path length
            var depth = 0
            var id = currentId
            while (id in prev) {
                depth++
                id = prev[id]!!.first
            }
            if (depth >= MAX_DEPTH) continue

            // Load edges for current node
            val outEdges = edgeRepository.findAllBySrcIdIn(setOf(currentId))
            val inEdges = edgeRepository.findAllByDstIdIn(setOf(currentId))

            val neighbors = mutableListOf<Triple<Long, Double, Edge>>()
            for (edge in outEdges) {
                val neighborId = edge.dst.id ?: continue
                val cost = EDGE_COSTS[edge.kind] ?: DEFAULT_COST
                neighbors.add(Triple(neighborId, cost, edge))
            }
            for (edge in inEdges) {
                val neighborId = edge.src.id ?: continue
                val cost = EDGE_COSTS[edge.kind] ?: DEFAULT_COST
                neighbors.add(Triple(neighborId, cost, edge))
            }

            for ((neighborId, edgeCost, edge) in neighbors) {
                if (neighborId in visited) continue
                val newCost = currentCost + edgeCost
                if (newCost < (dist[neighborId] ?: Double.MAX_VALUE)) {
                    dist[neighborId] = newCost
                    prev[neighborId] = currentId to edge
                    queue.add(QueueEntry(neighborId, newCost))
                }
            }
        }

        if (toId !in dist) {
            log.debug("No path found from {} to {} within {} hops", fromId, toId, MAX_DEPTH)
            return null
        }

        // Reconstruct path
        val pathEdges = mutableListOf<Edge>()
        val pathNodeIds = mutableListOf<Long>()
        var current = toId
        pathNodeIds.add(current)
        while (current in prev) {
            val (prevId, edge) = prev[current]!!
            pathEdges.add(edge)
            pathNodeIds.add(prevId)
            current = prevId
        }
        pathNodeIds.reverse()
        pathEdges.reverse()

        val nodes = nodeRepository.findAllByIdInBatched(pathNodeIds.toSet())
        val nodeMap = nodes.associateBy { it.id }
        val orderedNodes = pathNodeIds.mapNotNull { nodeMap[it] }

        return GraphPath(
            nodes = orderedNodes,
            edges = pathEdges,
            totalCost = dist[toId]!!,
        )
    }

    /**
     * Находит кратчайшие пути между всеми парами заданных узлов.
     * Полезно для ARCHITECTURE запросов, чтобы показать связи между найденными компонентами.
     */
    fun findPathsBetweenNodes(nodeIds: List<Long>): List<GraphPath> {
        if (nodeIds.size < 2) return emptyList()

        val paths = mutableListOf<GraphPath>()
        val pairs = mutableSetOf<Pair<Long, Long>>()

        // Generate unique pairs
        for (i in nodeIds.indices) {
            for (j in i + 1 until nodeIds.size) {
                pairs.add(nodeIds[i] to nodeIds[j])
            }
        }

        for ((from, to) in pairs) {
            if (paths.size >= MAX_PATHS) break
            val path = findShortestPath(from, to)
            if (path != null && path.edges.isNotEmpty()) {
                paths.add(path)
            }
        }

        log.info("Found {} paths between {} nodes", paths.size, nodeIds.size)
        return paths.sortedBy { it.totalCost }
    }

    /**
     * Форматирует пути в текстовый вид для включения в RAG-контекст.
     */
    fun formatPaths(paths: List<GraphPath>): String {
        if (paths.isEmpty()) return ""

        return buildString {
            appendLine("ПУТИ МЕЖДУ КОМПОНЕНТАМИ:")
            for ((idx, path) in paths.withIndex()) {
                append("Путь ${idx + 1}: ")
                val labels = path.nodes.map { it.name ?: it.fqn }
                appendLine(labels.joinToString(" → "))

                for (edge in path.edges) {
                    val srcLabel = edge.src.name ?: edge.src.fqn
                    val dstLabel = edge.dst.name ?: edge.dst.fqn
                    appendLine("  - $srcLabel --[${edge.kind}]--> $dstLabel")
                }
                appendLine("  Стоимость: ${"%.2f".format(path.totalCost)}")
                appendLine()
            }
        }
    }
}

/**
 * Кратчайший путь между двумя узлами.
 */
data class GraphPath(
    val nodes: List<Node>,
    val edges: List<Edge>,
    val totalCost: Double,
)
