package com.bftcom.docgenerator.rag.impl.research

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.embedding.api.EmbeddingSearchService
import com.bftcom.docgenerator.rag.impl.pathfinding.GraphPathFinder
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class ResearchTools(
    private val nodeRepository: NodeRepository,
    private val edgeRepository: EdgeRepository,
    private val embeddingSearchService: EmbeddingSearchService,
    private val graphPathFinder: GraphPathFinder,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_RESULTS = 10
        private const val MAX_CODE_CHARS = 2000
        private const val MAX_OBSERVATION_CHARS = 4000
    }

    /**
     * Ищет ноды по имени класса/метода/FQN-паттерну.
     * Вход: строка поиска (например "UserService" или "controller")
     */
    fun searchNodes(input: String, appId: Long?): String {
        val query = input.trim()
        if (query.isBlank()) return "Ошибка: пустой поисковый запрос"
        if (appId == null) return "Ошибка: не указан applicationId"

        val results = mutableListOf<Node>()

        // Поиск по FQN (содержащий паттерн)
        val byFqn = nodeRepository.findByApplicationIdAndFqnContaining(appId, query)
        results.addAll(byFqn)

        // Поиск по имени класса (case-insensitive)
        if (results.size < MAX_RESULTS) {
            val classKinds = setOf(NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.ENUM, NodeKind.SERVICE, NodeKind.RECORD)
            val byClass = nodeRepository.findByApplicationIdAndClassNameIgnoreCase(appId, query, classKinds)
            results.addAll(byClass)
        }

        // Поиск по имени метода (case-insensitive)
        if (results.size < MAX_RESULTS) {
            val byMethod = nodeRepository.findByApplicationIdAndMethodNameIgnoreCase(appId, query, NodeKind.METHOD)
            results.addAll(byMethod)
        }

        val unique = results.distinctBy { it.id }.take(MAX_RESULTS)
        if (unique.isEmpty()) return "Ничего не найдено по запросу: $query"

        return buildString {
            appendLine("Найдено ${unique.size} узлов:")
            for (node in unique) {
                appendLine("- [id=${node.id}] ${node.kind}: ${node.fqn} (name=${node.name ?: "-"})")
            }
        }.truncate()
    }

    /**
     * Hybrid search по эмбеддингам. Возвращает top-5 чанков с контентом.
     * Вход: описание того, что ищешь (например "обработка платежей")
     */
    fun searchCode(input: String, appId: Long?): String {
        val query = input.trim()
        if (query.isBlank()) return "Ошибка: пустой поисковый запрос"

        val results = embeddingSearchService.hybridSearch(
            query = query,
            topK = 5,
            applicationId = appId,
        )

        if (results.isEmpty()) return "Ничего не найдено по семантическому запросу: $query"

        return buildString {
            appendLine("Найдено ${results.size} фрагментов кода:")
            for ((idx, r) in results.withIndex()) {
                val nodeId = r.metadata["node_id"] ?: r.id
                val fqn = r.metadata["fqn"] ?: "unknown"
                appendLine("--- Фрагмент ${idx + 1} (id=$nodeId, fqn=$fqn, similarity=${"%.3f".format(r.similarity)}) ---")
                appendLine(r.content.take(MAX_CODE_CHARS))
                appendLine()
            }
        }.truncate()
    }

    /**
     * Получает полную инфо о ноде по ID: fqn, kind, sourceCode, signature, docComment.
     * Вход: ID узла (число)
     */
    fun getNodeDetails(input: String, appId: Long?): String {
        val nodeId = input.trim().toLongOrNull()
            ?: return "Ошибка: некорректный ID узла. Ожидается число, получено: $input"

        val node = nodeRepository.findById(nodeId).orElse(null)
            ?: return "Узел с id=$nodeId не найден"

        return buildString {
            appendLine("=== Узел id=${node.id} ===")
            appendLine("FQN: ${node.fqn}")
            appendLine("Kind: ${node.kind}")
            appendLine("Name: ${node.name ?: "-"}")
            appendLine("Package: ${node.packageName ?: "-"}")
            appendLine("File: ${node.filePath ?: "-"}")
            appendLine("Lines: ${node.lineStart ?: "?"}..${node.lineEnd ?: "?"}")
            if (!node.signature.isNullOrBlank()) {
                appendLine("Signature: ${node.signature}")
            }
            if (!node.docComment.isNullOrBlank()) {
                appendLine("Doc: ${node.docComment}")
            }
            if (!node.sourceCode.isNullOrBlank()) {
                appendLine("--- Source Code ---")
                appendLine(node.sourceCode!!.take(MAX_CODE_CHARS))
                if (node.sourceCode!!.length > MAX_CODE_CHARS) {
                    appendLine("... (обрезано, всего ${node.sourceCode!!.length} символов)")
                }
            } else {
                appendLine("(исходный код отсутствует)")
            }
        }.truncate()
    }

    /**
     * BFS от nodeId на depth хопов. Возвращает соседей и рёбра.
     * Вход: ID узла и глубина через запятую (например "123,2")
     */
    fun exploreGraph(input: String, appId: Long?): String {
        val parts = input.trim().split(",").map { it.trim() }
        val nodeId = parts.getOrNull(0)?.toLongOrNull()
            ?: return "Ошибка: ожидается формат 'nodeId,depth' (например '123,2')"
        val depth = (parts.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, 3)

        val rootNode = nodeRepository.findById(nodeId).orElse(null)
            ?: return "Узел с id=$nodeId не найден"

        // BFS
        val visited = mutableSetOf(nodeId)
        var frontier = setOf(nodeId)
        val edgeDescriptions = mutableListOf<String>()

        for (hop in 1..depth) {
            if (frontier.isEmpty()) break
            val edges = edgeRepository.findAllBySrcIdInOrDstIdIn(frontier)
            val nextFrontier = mutableSetOf<Long>()

            for (edge in edges) {
                val srcId = edge.src.id ?: continue
                val dstId = edge.dst.id ?: continue
                val srcLabel = edge.src.name ?: edge.src.fqn
                val dstLabel = edge.dst.name ?: edge.dst.fqn
                edgeDescriptions.add("  $srcLabel --[${edge.kind}]--> $dstLabel (src=$srcId, dst=$dstId)")

                if (srcId !in visited) { visited.add(srcId); nextFrontier.add(srcId) }
                if (dstId !in visited) { visited.add(dstId); nextFrontier.add(dstId) }
            }
            frontier = nextFrontier
        }

        if (edgeDescriptions.isEmpty()) {
            return "Узел ${rootNode.fqn} (id=$nodeId) не имеет связей в пределах $depth хопов"
        }

        return buildString {
            appendLine("Граф вокруг ${rootNode.fqn} (id=$nodeId), глубина=$depth:")
            appendLine("Найдено ${edgeDescriptions.size} связей, ${visited.size} узлов:")
            edgeDescriptions.take(30).forEach { appendLine(it) }
            if (edgeDescriptions.size > 30) {
                appendLine("... и ещё ${edgeDescriptions.size - 30} связей")
            }
        }.truncate()
    }

    /**
     * Dijkstra между двумя nodeId. Возвращает путь с рёбрами.
     * Вход: два ID через запятую (например "123,456")
     */
    fun findPaths(input: String, appId: Long?): String {
        val parts = input.trim().split(",").map { it.trim() }
        val fromId = parts.getOrNull(0)?.toLongOrNull()
            ?: return "Ошибка: ожидается формат 'fromId,toId' (например '123,456')"
        val toId = parts.getOrNull(1)?.toLongOrNull()
            ?: return "Ошибка: ожидается формат 'fromId,toId' (например '123,456')"

        val path = graphPathFinder.findShortestPath(fromId, toId)
            ?: return "Путь между узлами $fromId и $toId не найден"

        return buildString {
            appendLine("Путь от ${path.nodes.first().fqn} до ${path.nodes.last().fqn}:")
            appendLine("Стоимость: ${"%.2f".format(path.totalCost)}, хопов: ${path.edges.size}")
            val labels = path.nodes.map { "${it.name ?: it.fqn} (id=${it.id})" }
            appendLine("Маршрут: ${labels.joinToString(" → ")}")
            appendLine()
            for (edge in path.edges) {
                val srcLabel = edge.src.name ?: edge.src.fqn
                val dstLabel = edge.dst.name ?: edge.dst.fqn
                appendLine("  $srcLabel --[${edge.kind}]--> $dstLabel")
            }
        }.truncate()
    }

    /**
     * Обзор приложения: количество нод по kind, top endpoints, top classes.
     * Вход: пустая строка или фильтр по kind
     */
    fun listOverview(input: String, appId: Long?): String {
        if (appId == null) return "Ошибка: не указан applicationId"

        val kindFilter = input.trim().uppercase().takeIf { it.isNotBlank() }

        if (kindFilter != null) {
            val kind = try { NodeKind.valueOf(kindFilter) } catch (_: Exception) {
                return "Неизвестный kind: $kindFilter. Доступные: ${NodeKind.entries.joinToString()}"
            }
            val nodes = nodeRepository.findAllByApplicationIdAndKindIn(
                appId, setOf(kind), PageRequest.of(0, 20),
            )
            if (nodes.isEmpty()) return "Нет узлов вида $kind"
            return buildString {
                appendLine("Узлы вида $kind (top ${nodes.size}):")
                for (n in nodes) {
                    appendLine("- [id=${n.id}] ${n.fqn} (name=${n.name ?: "-"})")
                }
            }.truncate()
        }

        // Общий обзор — количество нод по kind
        val allNodes = nodeRepository.findAllByApplicationId(appId, PageRequest.of(0, 5000))
        val byKind = allNodes.groupBy { it.kind }.mapValues { it.value.size }
            .entries.sortedByDescending { it.value }

        return buildString {
            appendLine("Обзор приложения (applicationId=$appId):")
            appendLine("Всего узлов: ${allNodes.size}")
            appendLine()
            appendLine("По видам:")
            for ((kind, count) in byKind) {
                appendLine("  $kind: $count")
            }

            // Top endpoints
            val endpoints = allNodes.filter { it.kind == NodeKind.ENDPOINT }.take(10)
            if (endpoints.isNotEmpty()) {
                appendLine()
                appendLine("Top эндпоинты:")
                for (ep in endpoints) {
                    appendLine("  - [id=${ep.id}] ${ep.fqn} (name=${ep.name ?: "-"})")
                }
            }

            // Top classes
            val classes = allNodes.filter { it.kind == NodeKind.CLASS }.take(10)
            if (classes.isNotEmpty()) {
                appendLine()
                appendLine("Top классы:")
                for (cls in classes) {
                    appendLine("  - [id=${cls.id}] ${cls.fqn} (name=${cls.name ?: "-"})")
                }
            }
        }.truncate()
    }

    private fun String.truncate(): String {
        return if (length > MAX_OBSERVATION_CHARS) {
            take(MAX_OBSERVATION_CHARS) + "\n... (обрезано)"
        } else {
            this
        }
    }
}
