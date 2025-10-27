package com.bftcom.docgenerator.chunking.service

import com.bftcom.docgenerator.api.dto.graph.GEdge
import com.bftcom.docgenerator.api.dto.graph.GNode
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.repo.EdgeRepository
import com.bftcom.docgenerator.repo.NodeRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import kotlin.collections.setOfNotNull

@Repository
class ChunkGraphRepositoryImpl(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
) : ChunkGraphRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadNodes(appId: Long, kinds: Set<String>, limit: Int): List<GNode> {
        val nkinds: Set<NodeKind> = kinds
            .mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
            .mapNotNull { s -> runCatching { NodeKind.valueOf(s) }.getOrNull() }
            .toSet()
            .ifEmpty { NodeKind.entries.toSet() } // если не пришло — берём все

        val nodes: List<Node> = nodeRepo.findAllByApplicationIdAndKindIn(
            applicationId = appId,
            kinds = nkinds,
            pageable = PageRequest.of(0, limit.coerceAtLeast(1))
        )
        return nodes.map { it.toGNode() }
    }

    override fun loadEdges(appId: Long, nodeIds: Set<String>, withRelations: Boolean): List<GEdge> {
        if (nodeIds.isEmpty()) return emptyList()
        val ids = nodeIds.mapNotNull { it.toLongOrNull() }.toSet()
        if (ids.isEmpty()) return emptyList()

        // Берём рёбра, где либо src, либо dst в рассматриваемом подграфе
        log.info("Loading edges for nodeIds=${ids.take(10)} (size=${ids.size})")
        val out = edgeRepo.findAllBySrcIdIn(ids)
        val inc = edgeRepo.findAllByDstIdIn(ids)
        log.info("Edges out=${out.size}, inc=${inc.size}")

        val all = (out + inc)
            .distinctBy { setOfNotNull(it.dst.id, it.src.id) } // на случай пересечений

        // Если нужно, тут можно отфильтровать виды рёбер по твоей бизнес-логике (withRelations)
        // Например:
        // val allowedKinds = if (withRelations) setOf(EdgeKind.CALLS, EdgeKind.RELATES, EdgeKind.USES) else setOf(EdgeKind.CALLS)
        // val filtered = all.filter { it.kind in allowedKinds }

        return all.map { it.toGEdge() }
    }

    override fun loadNeighbors(nodeId: String, limit: Int): List<GNode> {
        val id = nodeId.toLongOrNull() ?: return emptyList()

        val outgoing = edgeRepo.findAllBySrcId(id)
        val incoming = edgeRepo.findAllByDstId(id)

        val neighborIds: Set<Long> = buildSet {
            addAll(outgoing.map { it.dst.id ?: throw RuntimeException() })
            addAll(incoming.map { it.src.id ?: throw RuntimeException() })
        }.take(limit.coerceAtLeast(1)).toSet()

        if (neighborIds.isEmpty()) return emptyList()

        val neighbors = nodeRepo.findAllByIdIn(neighborIds)
        return neighbors.map { it.toGNode() }
    }

    override fun loadEdgesByNode(nodeId: String, neighborIds: Set<String>): List<GEdge> {
        val id = nodeId.toLongOrNull() ?: return emptyList()
        val nbr = neighborIds.mapNotNull { it.toLongOrNull() }.toSet()
        if (nbr.isEmpty()) return emptyList()

        val out = edgeRepo.findAllBySrcIdAndDstIdIn(id, nbr)
        val inc = edgeRepo.findAllByDstIdAndSrcIdIn(id, nbr)

        return (out + inc).distinctBy { setOfNotNull(it.dst.id, it.src.id) }.map { it.toGEdge() }
    }

    private fun Node.toGNode(): GNode =
        GNode(
            id = this.id.toString(),
            label = this.name ?: this.id.toString(),
            kind = this.kind.name,
            group = this.packageName,
            // size можно вычислять отдельно (degree), но это доп.запросы — оставим null
            size = null,
            color = null,
            meta = emptyMap()
        )

    private fun Edge.toGEdge(): GEdge =
        GEdge(
            id = setOfNotNull(this.dst.id, this.src.id).toString(),
            source = this.src.id.toString(),
            target = this.dst.id.toString(),
            kind = this.kind.name,
            weight = null
        )
}