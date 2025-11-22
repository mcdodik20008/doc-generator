package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.domain.node.RawUsage
import com.bftcom.docgenerator.graph.api.library.LibraryNodeIndex
import com.bftcom.docgenerator.graph.api.linker.GraphLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import com.bftcom.docgenerator.graph.api.linker.sink.GraphSink
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Сервис для линковки узлов графа - создания рёбер между узлами.
 */
@Service
class GraphLinkerImpl(
    private val nodeRepo: NodeRepository,
    private val nodeIndexFactory: NodeIndexFactory,
    private val sink: GraphSink,
    private val objectMapper: ObjectMapper,
    private val libraryNodeIndex: LibraryNodeIndex,
    private val integrationPointService: IntegrationPointService,
) : GraphLinker {
    companion object {
        private val log = LoggerFactory.getLogger(GraphLinker::class.java)
        private val TYPE_TOKEN = Regex("""\:\s*([A-Za-z_][A-Za-z0-9_\.]*)""")
    }

    @Transactional
    override fun link(application: Application) {
        log.info("Starting graph linking for app [id=${application.id}]...")

        val all = nodeRepo.findAllByApplicationId(application.id!!, Pageable.ofSize(Int.MAX_VALUE))
        if (all.isEmpty()) {
            log.warn("No nodes found; skipping.")
            return
        }
        log.info("Fetched ${all.size} total nodes to link.")

        // Используем мутабельный индекс, чтобы можно было добавлять новые узлы
        val index = nodeIndexFactory.createMutable(all)

        fun metaOf(n: Node): NodeMeta = objectMapper.convertValue(n.meta, NodeMeta::class.java)

        val edges = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val newlyCreatedNodes = mutableListOf<Node>()

        // === STRUCTURE ===
        edges += linkContains(all, index, ::metaOf)

        var callsErrors = 0
        all.forEachIndexed { i, node ->
            val p = ((i + 1) * 100.0 / all.size).toInt()
            log.info("[${i + 1}/${all.size}, $p%] Linking: ${node.kind} ${node.fqn}")

            val meta = metaOf(node)
            if (node.isTypeNode()) edges += linkInheritsImplements(node, meta, index)
            edges += linkAnnotations(node, meta, index)
            if (node.isFunctionNode()) {
                edges += linkSignatureDepends(node, meta, index)
                try {
                    edges += linkCalls(node, meta, index)
                    // Создаем интеграционные Edge на основе LibraryNode
                    // Собираем новые узлы для обновления индекса
                    val (integrationEdges, newNodes) = linkIntegrationEdgesWithNodes(node, meta, index, application)
                    edges += integrationEdges
                    newlyCreatedNodes += newNodes
                } catch (e: Exception) {
                    callsErrors++
                    log.error("CALLS linking failed for ${node.fqn}: ${e.message}", e)
                }
                edges += linkThrows(node, meta, index)
            }
        }

        // Обновляем индекс новыми узлами
        if (newlyCreatedNodes.isNotEmpty()) {
            log.info("Updating index with ${newlyCreatedNodes.size} newly created nodes (ENDPOINT/TOPIC)")
            if (index is NodeIndexFactory.MutableNodeIndex) {
                index.addNodes(newlyCreatedNodes)
            }
        }

        // === persist ===
        sink.upsertEdges(
            edges.asSequence().map { (src, dst, kind) ->
                SimpleEdgeProposal(kind, src, dst)
            },
        )

        log.info("Finished linking. CALLS errors: $callsErrors, new integration nodes: ${newlyCreatedNodes.size}")
    }

    // ================= helpers =================

    private fun linkContains(
        all: List<Node>,
        index: NodeIndex,
        metaOf: (Node) -> NodeMeta,
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()

        all
            .filter {
                it.kind in
                    setOf(
                        NodeKind.INTERFACE,
                        NodeKind.SERVICE,
                        NodeKind.RECORD,
                        NodeKind.MAPPER,
                        NodeKind.ENDPOINT,
                        NodeKind.CLASS,
                        NodeKind.ENUM,
                        NodeKind.CONFIG,
                    )
            }.forEach { type ->
                val pkg = index.findByFqn(type.packageName ?: return@forEach) ?: return@forEach
                res += Triple(pkg, type, EdgeKind.CONTAINS)
            }

        all
            .filter {
                it.kind in
                    setOf(
                        NodeKind.METHOD,
                        NodeKind.FIELD,
                        NodeKind.ENDPOINT,
                        NodeKind.JOB,
                        NodeKind.TOPIC,
                    )
            }.forEach { member ->
                val ownerFqn = metaOf(member).ownerFqn ?: return@forEach
                val owner = index.findByFqn(ownerFqn) ?: return@forEach
                res += Triple(owner, member, EdgeKind.CONTAINS)
            }

        return res
    }

    private fun linkInheritsImplements(
        node: Node,
        meta: NodeMeta,
        index: NodeIndex,
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val imports = meta.imports ?: emptyList()
        val pkg = node.packageName.orEmpty()
        val candidates = (meta.supertypesResolved ?: emptyList()) + (meta.supertypesSimple ?: emptyList())

        for (raw in candidates) {
            val target = index.resolveType(raw, imports, pkg) ?: continue
            when (target.kind) {
                NodeKind.INTERFACE -> {
                    res += Triple(node, target, EdgeKind.IMPLEMENTS)
                    res += Triple(node, target, EdgeKind.DEPENDS_ON)
                }
                else -> {
                    res += Triple(node, target, EdgeKind.INHERITS)
                    res += Triple(node, target, EdgeKind.DEPENDS_ON)
                }
            }
        }
        return res
    }

    private fun linkAnnotations(
        node: Node,
        meta: NodeMeta,
        index: NodeIndex,
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val annotations = meta.annotations ?: return emptyList()
        val imports = meta.imports ?: emptyList()
        val pkg = node.packageName.orEmpty()

        for (a in annotations) {
            val t = index.resolveType(a, imports, pkg) ?: continue
            res += Triple(node, t, EdgeKind.ANNOTATED_WITH)
            res += Triple(node, t, EdgeKind.DEPENDS_ON)
        }
        return res
    }

    private fun linkSignatureDepends(
        fn: Node,
        meta: NodeMeta,
        index: NodeIndex,
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val imports = meta.imports ?: emptyList()
        val pkg = fn.packageName.orEmpty()

        val tokens: Set<String> =
            when {
                !meta.paramTypes.isNullOrEmpty() || !meta.returnType.isNullOrBlank() ->
                    (meta.paramTypes.orEmpty() + listOfNotNull(meta.returnType)).toSet()
                !fn.signature.isNullOrBlank() ->
                    TYPE_TOKEN
                        .findAll(fn.signature!!)
                        .map { it.groupValues[1].substringBefore('<').substringBefore('?') }
                        .toSet()
                else -> emptySet()
            }

        val ownerFqn = meta.ownerFqn
        val src = ownerFqn?.let { index.findByFqn(it) } ?: fn

        for (t in tokens) {
            val typeNode = index.resolveType(t, imports, pkg) ?: continue
            if (typeNode.id != src.id) res += Triple(src, typeNode, EdgeKind.DEPENDS_ON)
        }
        return res
    }

    private fun linkCalls(
        fn: Node,
        meta: NodeMeta,
        index: NodeIndex,
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val usages = meta.rawUsages ?: return emptyList()
        val imports = meta.imports ?: emptyList()
        val owner = meta.ownerFqn?.let { index.findByFqn(it) }
        val pkg = fn.packageName.orEmpty()

        usages.forEach { u ->
            when (u) {
                is RawUsage.Simple -> {
                    if (owner != null) {
                        index.findByFqn("${owner.fqn}.${u.name}")?.let {
                            res += Triple(fn, it, EdgeKind.CALLS)
                            return@forEach
                        }
                    }
                    if (u.isCall) {
                        index.resolveType(u.name, imports, pkg)?.let {
                            res += Triple(fn, it, EdgeKind.CALLS)
                        }
                    }
                }
                is RawUsage.Dot -> {
                    val recvType =
                        if (u.receiver.firstOrNull()?.isUpperCase() == true) {
                            index.resolveType(u.receiver, imports, pkg)
                        } else {
                            owner
                        }
                    recvType?.let { r ->
                        index.findByFqn("${r.fqn}.${u.member}")?.let {
                            res += Triple(fn, it, EdgeKind.CALLS)
                        }
                    }
                }
            }
        }
        return res
    }

    private fun linkThrows(
        fn: Node,
        meta: NodeMeta,
        index: NodeIndex,
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val throwsTypes = meta.throwsTypes ?: return emptyList()
        val imports = meta.imports ?: emptyList()
        val pkg = fn.packageName.orEmpty()

        throwsTypes.forEach { throwType ->
            index.resolveType(throwType, imports, pkg)?.let {
                res += Triple(fn, it, EdgeKind.THROWS)
            }
        }
        return res
    }

    private fun Node.isTypeNode(): Boolean =
        kind in
            setOf(
                NodeKind.CLASS,
                NodeKind.INTERFACE,
                NodeKind.ENUM,
                NodeKind.RECORD,
                NodeKind.SERVICE,
                NodeKind.MAPPER,
                NodeKind.CONFIG,
            )

    private fun Node.isFunctionNode(): Boolean = kind in setOf(NodeKind.METHOD, NodeKind.ENDPOINT, NodeKind.JOB, NodeKind.TOPIC)

    /**
     * Создает интеграционные Edge (CALLS_HTTP, PRODUCES, CONSUMES) на основе LibraryNode.
     * Возвращает пару: (список Edge, список новых созданных узлов).
     */
    private fun linkIntegrationEdgesWithNodes(
        fn: Node,
        meta: NodeMeta,
        index: NodeIndex,
        application: Application,
    ): Pair<List<Triple<Node, Node, EdgeKind>>, List<Node>> {
        val edges = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val newNodes = mutableListOf<Node>()

        val result = linkIntegrationEdgesInternal(fn, meta, index, application)
        edges += result.first
        newNodes += result.second

        return Pair(edges, newNodes)
    }

    /**
     * Создает интеграционные Edge (CALLS_HTTP, PRODUCES, CONSUMES) на основе LibraryNode.
     *
     * Алгоритм:
     * 1. Анализирует rawUsages метода приложения
     * 2. Для каждого вызова метода библиотеки проверяет, есть ли интеграционные точки
     * 3. Создает соответствующие Edge и виртуальные узлы (ENDPOINT, TOPIC)
     */
    private fun linkIntegrationEdges(
        fn: Node,
        meta: NodeMeta,
        index: NodeIndex,
    ): List<Triple<Node, Node, EdgeKind>> = linkIntegrationEdgesInternal(fn, meta, index, fn.application).first

    private fun linkIntegrationEdgesInternal(
        fn: Node,
        meta: NodeMeta,
        index: NodeIndex,
        application: Application,
    ): Pair<List<Triple<Node, Node, EdgeKind>>, List<Node>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val newNodes = mutableListOf<Node>()
        val usages = meta.rawUsages ?: return Pair(emptyList(), emptyList())
        val imports = meta.imports ?: emptyList()
        val owner = meta.ownerFqn?.let { index.findByFqn(it) }
        val pkg = fn.packageName.orEmpty()

        usages.forEach { u ->
            // Пытаемся найти метод в библиотеках
            val libraryMethodFqn =
                when (u) {
                    is RawUsage.Simple -> {
                        if (owner != null) {
                            "${owner.fqn}.${u.name}"
                        } else {
                            // Пытаемся разрешить через imports
                            imports.firstOrNull { it.endsWith(".${u.name}") }?.let { "$it.${u.name}" }
                                ?: if (u.name.contains('.')) u.name else null
                        }
                    }
                    is RawUsage.Dot -> {
                        val recvType =
                            if (u.receiver.firstOrNull()?.isUpperCase() == true) {
                                index.resolveType(u.receiver, imports, pkg)?.fqn
                            } else {
                                owner?.fqn
                            }
                        recvType?.let { "$it.${u.member}" }
                    }
                }

            if (libraryMethodFqn != null) {
                val libraryNode = libraryNodeIndex.findByMethodFqn(libraryMethodFqn)
                if (libraryNode != null) {
                    // Нашли метод в библиотеке - извлекаем интеграционные точки
                    val integrationPoints = integrationPointService.extractIntegrationPoints(libraryNode)

                    for (point in integrationPoints) {
                        when (point) {
                            is com.bftcom.docgenerator.library.api.integration.IntegrationPoint.HttpEndpoint -> {
                                // Создаем или находим узел ENDPOINT
                                val (endpointNode, isNew) =
                                    getOrCreateEndpointNode(
                                        url = point.url ?: "unknown",
                                        httpMethod = point.httpMethod,
                                        index = index,
                                        application = application,
                                    )
                                if (endpointNode != null) {
                                    if (isNew) {
                                        newNodes.add(endpointNode)
                                    }
                                    res += Triple(fn, endpointNode, EdgeKind.CALLS_HTTP)

                                    // Создаем дополнительные Edge для retry/timeout/circuit breaker
                                    if (point.hasRetry) {
                                        res += Triple(fn, endpointNode, EdgeKind.RETRIES_TO)
                                    }
                                    if (point.hasTimeout) {
                                        res += Triple(fn, endpointNode, EdgeKind.TIMEOUTS_TO)
                                    }
                                    if (point.hasCircuitBreaker) {
                                        res += Triple(fn, endpointNode, EdgeKind.CIRCUIT_BREAKER_TO)
                                    }
                                }
                            }
                            is com.bftcom.docgenerator.library.api.integration.IntegrationPoint.KafkaTopic -> {
                                // Создаем или находим узел TOPIC
                                val (topicNode, isNew) =
                                    getOrCreateTopicNode(
                                        topic = point.topic ?: "unknown",
                                        index = index,
                                        application = application,
                                    )
                                if (topicNode != null) {
                                    if (isNew) {
                                        newNodes.add(topicNode)
                                    }
                                    when (point.operation) {
                                        "PRODUCE" -> res += Triple(fn, topicNode, EdgeKind.PRODUCES)
                                        "CONSUME" -> res += Triple(fn, topicNode, EdgeKind.CONSUMES)
                                    }
                                }
                            }
                            is com.bftcom.docgenerator.library.api.integration.IntegrationPoint.CamelRoute -> {
                                // Для Camel создаем ENDPOINT узел
                                val (endpointNode, isNew) =
                                    getOrCreateEndpointNode(
                                        url = point.uri ?: "unknown",
                                        httpMethod = null,
                                        index = index,
                                        application = application,
                                    )
                                if (endpointNode != null) {
                                    if (isNew) {
                                        newNodes.add(endpointNode)
                                    }
                                    // Camel может быть как HTTP, так и другими протоколами
                                    if (point.endpointType == "http" || point.uri?.startsWith("http") == true) {
                                        res += Triple(fn, endpointNode, EdgeKind.CALLS_HTTP)
                                    }
                                    // TODO: можно добавить другие типы Camel endpoints
                                }
                            }
                        }
                    }
                }
            }
        }

        return Pair(res, newNodes)
    }

    /**
     * Создает или находит узел ENDPOINT для указанного URL.
     * Возвращает пару: (узел, был ли создан новый узел).
     */
    private fun getOrCreateEndpointNode(
        url: String,
        httpMethod: String?,
        index: NodeIndex,
        application: com.bftcom.docgenerator.domain.application.Application,
    ): Pair<Node?, Boolean> {
        // Создаем FQN для endpoint: "endpoint://{httpMethod} {url}"
        val endpointFqn =
            if (httpMethod != null) {
                "endpoint://$httpMethod $url"
            } else {
                "endpoint://$url"
            }

        // Пытаемся найти существующий узел
        val existing = index.findByFqn(endpointFqn)
        if (existing != null) {
            return Pair(existing, false)
        }

        // Создаем новый узел ENDPOINT
        try {
            val endpointName = url.substringAfterLast('/').takeIf { it.isNotBlank() } ?: url
            val endpointNode =
                nodeRepo.save(
                    Node(
                        application = application,
                        fqn = endpointFqn,
                        name = endpointName,
                        packageName = null,
                        kind = NodeKind.ENDPOINT,
                        lang = com.bftcom.docgenerator.domain.enums.Lang.java, // виртуальный узел
                        parent = null,
                        filePath = null,
                        lineStart = null,
                        lineEnd = null,
                        sourceCode = null,
                        docComment = null,
                        signature = null,
                        codeHash = null,
                        meta =
                            mapOf(
                                "url" to url,
                                "httpMethod" to (httpMethod ?: "UNKNOWN"),
                                "source" to "library_analysis",
                            ),
                    ),
                )
            log.debug("Created ENDPOINT node: {}", endpointFqn)
            return Pair(endpointNode, true)
        } catch (e: Exception) {
            log.warn("Failed to create ENDPOINT node {}: {}", endpointFqn, e.message)
            return Pair(null, false)
        }
    }

    /**
     * Создает или находит узел TOPIC для указанного Kafka topic.
     * Возвращает пару: (узел, был ли создан новый узел).
     */
    private fun getOrCreateTopicNode(
        topic: String,
        index: NodeIndex,
        application: com.bftcom.docgenerator.domain.application.Application,
    ): Pair<Node?, Boolean> {
        val topicFqn = "topic://$topic"

        val existing = index.findByFqn(topicFqn)
        if (existing != null) {
            return Pair(existing, false)
        }

        // Создаем новый узел TOPIC
        try {
            val topicNode =
                nodeRepo.save(
                    Node(
                        application = application,
                        fqn = topicFqn,
                        name = topic,
                        packageName = null,
                        kind = NodeKind.TOPIC,
                        lang = com.bftcom.docgenerator.domain.enums.Lang.java, // виртуальный узел
                        parent = null,
                        filePath = null,
                        lineStart = null,
                        lineEnd = null,
                        sourceCode = null,
                        docComment = null,
                        signature = null,
                        codeHash = null,
                        meta =
                            mapOf(
                                "topic" to topic,
                                "source" to "library_analysis",
                            ),
                    ),
                )
            log.debug("Created TOPIC node: {}", topicFqn)
            return Pair(topicNode, true)
        } catch (e: Exception) {
            log.warn("Failed to create TOPIC node {}: {}", topicFqn, e.message)
            return Pair(null, false)
        }
    }
}
