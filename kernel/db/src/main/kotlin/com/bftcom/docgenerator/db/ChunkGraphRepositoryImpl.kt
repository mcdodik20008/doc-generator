package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.dto.GEdge
import com.bftcom.docgenerator.domain.dto.GNode
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository

@Repository
open class ChunkGraphRepositoryImpl(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
) : ChunkGraphRepository {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadNodes(
        appId: Long,
        kinds: Set<String>,
        limit: Int,
    ): List<GNode> {
        // Валидация appId
        if (appId <= 0) {
            log.warn("Invalid appId=$appId in loadNodes, must be positive")
            return emptyList()
        }

        val trimmedKinds = kinds.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
        val invalidKinds = mutableListOf<String>()

        val nkinds: Set<NodeKind> = trimmedKinds
            .mapNotNull { s ->
                runCatching { NodeKind.valueOf(s) }.getOrElse {
                    invalidKinds.add(s)
                    null
                }
            }
            .toSet()
            .ifEmpty { NodeKind.entries.toSet() } // если не пришло — берём все

        if (invalidKinds.isNotEmpty()) {
            log.warn("Invalid NodeKind values in loadNodes: $invalidKinds. Valid values: ${NodeKind.entries.map { it.name }}")
        }

        // Используем сортировку по fqn для предсказуемого порядка результатов
        val nodes: List<Node> =
            nodeRepo.findAllByApplicationIdAndKindIn(
                applicationId = appId,
                kinds = nkinds,
                pageable = PageRequest.of(0, limit.coerceAtLeast(1), Sort.by("fqn").ascending()),
            )
        return nodes.map { it.toGNode() }
    }

    override fun loadEdges(
        appId: Long,
        nodeIds: Set<String>,
        withRelations: Boolean,
    ): List<GEdge> {
        if (nodeIds.isEmpty()) return emptyList()

        // Валидация размера nodeIds для защиты от performance проблем
        if (nodeIds.size > 10000) {
            log.warn(
                "nodeIds set size (${nodeIds.size}) exceeds maximum 10000 for loadEdges. " +
                "This may cause performance issues. Consider batch processing."
            )
        }

        val ids = nodeIds.mapNotNull { it.toLongOrNull() }.toSet()
        if (ids.isEmpty()) return emptyList()

        // Берём рёбра, где либо src, либо dst в рассматриваемом подграфе
        // Логируем первые и последние IDs для лучшей диагностики проблем
        val idsPreview = if (ids.size <= 10) {
            ids.toString()
        } else {
            val sortedIds = ids.sorted()
            "first 5: ${sortedIds.take(5)}, last 5: ${sortedIds.takeLast(5)}"
        }
        log.info("Loading edges for nodeIds=$idsPreview (size=${ids.size}), withRelations=$withRelations")
        // Используем объединенный запрос вместо двух отдельных (фикс N+1 проблемы)
        // DISTINCT уже в SQL запросе, поэтому не нужен distinctBy в памяти
        val all = edgeRepo.findAllBySrcIdInOrDstIdIn(ids)

        // Фильтруем edges по withRelations параметру
        val filtered = if (withRelations) {
            // Возвращаем все типы рёбер включая отношения (RELATES, USES, INHERITS и т.д.)
            all
        } else {
            // Возвращаем только базовые связи вызовов
            all.filter { it.kind == com.bftcom.docgenerator.domain.enums.EdgeKind.CALLS }
        }

        log.info("Edges loaded=${all.size}, after filtering=${filtered.size}")

        return filtered.map { it.toGEdge() }
    }

    override fun loadNeighbors(
        nodeId: String,
        limit: Int,
    ): List<GNode> {
        val id = nodeId.toLongOrNull() ?: return emptyList()

        // Валидация limit: должен быть положительным и не превышать 10000
        val validatedLimit = when {
            limit <= 0 -> {
                log.warn("Invalid limit=$limit for nodeId=$nodeId, using default 100")
                100
            }
            limit > 10000 -> {
                log.warn("Limit=$limit exceeds maximum 10000 for nodeId=$nodeId, capping to 10000")
                10000
            }
            else -> limit
        }

        // Используем объединенный запрос вместо двух отдельных (фикс N+1 проблемы)
        val edges = edgeRepo.findAllBySrcIdInOrDstIdIn(setOf(id))

        val neighborIds: Set<Long> =
            edges.mapNotNull { edge ->
                if (edge.src.id == id) edge.dst.id else edge.src.id
            }.distinct().take(validatedLimit).toSet()

        if (neighborIds.isEmpty()) return emptyList()

        val neighbors = nodeRepo.findAllByIdInBatched(neighborIds)
        return neighbors.map { it.toGNode() }
    }

    override fun loadEdgesByNode(
        nodeId: String,
        neighborIds: Set<String>,
    ): List<GEdge> {
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
            meta = emptyMap(),
        )

    private fun Edge.toGEdge(): GEdge =
        GEdge(
            id = "${this.src.id}->${this.dst.id}:${this.kind.name}",
            source = this.src.id.toString(),
            target = this.dst.id.toString(),
            kind = this.kind.name,
            weight = null,
        )
}
