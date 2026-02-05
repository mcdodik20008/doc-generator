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

        // TODO: runCatching ловит все исключения - может скрыть реальные проблемы
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

        // TODO: PageRequest.of(0, limit) всегда берет первую страницу - нет возможности пагинации
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
        // TODO: Для больших ids наборов IN запросы могут быть очень медленными
        // TODO: Рассмотреть batch processing или использование JOIN вместо IN
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

        // TODO: map выполняется на всем списке в памяти - может быть медленно
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

        // TODO: Если id null, лучше пропустить edge с логированием, а не падать
        // TODO: take выполняется на Set, не на List - порядок недетерминирован
        // TODO: Логика take().toSet() странная - limit применяется до преобразования в Set
        val neighborIds: Set<Long> =
            buildSet {
                edges.forEach { edge ->
                    // Добавляем соседний узел (не сам id)
                    if (edge.src.id == id) {
                        edge.dst.id?.let { add(it) }
                    } else {
                        edge.src.id?.let { add(it) }
                    }
                }
            }.take(validatedLimit).toSet()

        if (neighborIds.isEmpty()) return emptyList()

        // TODO: Еще один запрос к БД - итого 2 запроса вместо одного с JOIN
        // TODO: Нет гарантии порядка результатов
        val neighbors = nodeRepo.findAllByIdIn(neighborIds)
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
            id = setOfNotNull(this.dst.id, this.src.id).toString(),
            source = this.src.id.toString(),
            target = this.dst.id.toString(),
            kind = this.kind.name,
            weight = null,
        )
}
