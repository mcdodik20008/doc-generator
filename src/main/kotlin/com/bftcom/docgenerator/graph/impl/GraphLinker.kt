package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.model.RawUsage
import com.bftcom.docgenerator.repo.EdgeRepository
import com.bftcom.docgenerator.repo.NodeRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * "Линкер" графа.
 * Читает `meta` из существующих нод и строит рёбра (CALLS, INHERITS, ...).
 */
@Service
class GraphLinker(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository
) {

    companion object {
        private val log = LoggerFactory.getLogger(GraphLinker::class.java)
    }

    /**
     * Главный метод: строит все рёбра для приложения.
     */
    @Transactional
    fun link(application: Application) {
        log.info("Starting graph linking for app [id=${application.id}]...")

        // Ты используешь Pageable, это нормально.
        // Если allNodes будет слишком большим, тут можно перейти на Pageable.unpaged() или PageRequest
        val allNodes = nodeRepo.findAllByApplicationId(application.id!!, Pageable.ofSize(Int.MAX_VALUE))
        log.info("Fetched ${allNodes.size} total nodes to link.")

        if (allNodes.isEmpty()) {
            log.warn("No nodes found for application [id=${application.id}]. Linking step will be skipped.")
            return
        }

        log.info("Building lookup maps (by FQN, by SimpleName, by ClassFields)...")
        val nodeByFqn = allNodes.associateBy { it.fqn }

        // Карта для "слабого" разрешения: "MyService" -> [Node(com.a.MyService), Node(com.b.MyService)]
        val nodesBySimpleName = allNodes.groupBy { it.name }

        // Карта полей для классов: ClassFQN -> FieldName -> FieldNode
        val fieldsByClassFqn = allNodes.filter { it.kind == NodeKind.FIELD }
            .groupBy { it.meta["ownerFqn"] as? String }
            .filterKeys { it != null }
            .mapValues { (_, fields) -> fields.associateBy { it.name } } as Map<String, Map<String?, Node>>

        // 2. Итерируемся и строим рёбра
        log.info("Starting edge creation loop over ${allNodes.size} nodes...")
        var linkCallsErrors = 0
        for (node in allNodes) {
            // A. Строим рёбра НАСЛЕДОВАНИЯ
            if (node.isTypeNode()) {
                linkInheritance(node, nodeByFqn, nodesBySimpleName)
            }

            // Б. Строим рёбра ВЫЗОВОВ
            if (node.isFunctionNode()) {
                try {
                    linkCalls(node, nodeByFqn, nodesBySimpleName, fieldsByClassFqn)
                } catch (e: Exception) {
                    // Ловим ошибки, чтобы одна плохая нода не уронила всю линковку
                    log.error("Failed to link calls for node [${node.fqn}]. Error: ${e.message}", e)
                    linkCallsErrors++
                }
            }
        }
        log.info("Graph linking complete. Processed ${allNodes.size} nodes. Failed node links: $linkCallsErrors.")
    }

    /**
     * Линкует рёбра IMPLEMENTS (для интерфейсов) и DEPENDS_ON (для классов).
     */
    private fun linkInheritance(
        node: Node, // Нода класса/интерфейса
        nodeByFqn: Map<String, Node>,
        nodesBySimpleName: Map<String?, List<Node>>
    ) {
        val supertypes = node.meta["supertypesSimple"] as? List<String> ?: return
        val pkgFqn = node.packageName ?: return

        for (superName in supertypes) {
            val targetNode =
                // Попытка 1: Тип в том же пакете
                nodeByFqn["$pkgFqn.$superName"]
                // Попытка 2: Первый попавшийся тип с таким же простым именем
                    ?: nodesBySimpleName[superName]?.firstOrNull { it.isTypeNode() }

            if (targetNode != null) {
                val kind = if (targetNode.kind == NodeKind.INTERFACE) {
                    EdgeKind.IMPLEMENTS
                } else {
                    EdgeKind.DEPENDS_ON
                }
                createEdge(node, targetNode, kind)
            }
        }
    }

    // --- [ВОТ ГЛАВНОЕ ИСПРАВЛЕНИЕ] ---

    /**
     * Линкует рёбра CALLS.
     * [ИСПРАВЛЕНО] - обрабатывает "горячие" (RawUsage) и "холодные" (Map) данные.
     */
    private fun linkCalls(
        node: Node, // Нода функции/метода
        nodeByFqn: Map<String, Node>,
        nodesBySimpleName: Map<String?, List<Node>>,
        fieldsByClassFqn: Map<String, Map<String?, Node>>
    ) {
        // 1. Десериализуем RawUsage из `meta`
        // Сначала получаем как "список чего-угодно"
        val rawUsagesAnyList = node.meta["rawUsages"] as? List<*> ?: return

        // --- [ИСПРАВЛЕНИЕ] ---
        // Проверяем тип каждого элемента, чтобы справиться и с Map, и с RawUsage
        val rawUsages = rawUsagesAnyList.mapNotNull { item ->
            when (item) {
                // Случай 1: "Горячие" данные, это уже наш data class
                is RawUsage -> item

                // Случай 2: "Холодные" данные (из JSON/DB), это Map
                is Map<*, *> -> {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        deserializeRawUsage(item as Map<String, Any>)
                    } catch (e: Exception) {
                        log.warn("Failed to deserialize RawUsage map for node [${node.fqn}]. Data: $item", e)
                        null
                    }
                }

                // Что-то другое, пропускаем
                else -> {
                    log.warn("Unknown data type in rawUsages meta for node [${node.fqn}]: ${item?.javaClass?.name}")
                    null
                }
            }
        }
        // --- [КОНЕЦ ИСПРАВЛЕНИЯ] ---


        val classFqn = node.meta["ownerFqn"] as? String

        for (usage in rawUsages) {
            when (usage) {
                is RawUsage.Simple -> {
                    // "Слабое" звено: `localFun()`
                    val targetFqn = if (classFqn != null) "$classFqn.${usage.name}" else null
                    val targetNode = nodeByFqn[targetFqn]
                    if (targetNode != null) {
                        createEdge(node, targetNode, EdgeKind.CALLS)
                    }
                }

                is RawUsage.Dot -> {
                    // "myService.doWork()"
                    val receiverTypeFqn = resolveReceiverType(
                        receiverName = usage.receiver,
                        classFqn = classFqn,
                        fieldsByClassFqn = fieldsByClassFqn,
                        nodeByFqn = nodeByFqn,
                        nodesBySimpleName = nodesBySimpleName
                    )

                    if (receiverTypeFqn != null) {
                        val targetNode = nodeByFqn["$receiverTypeFqn.${usage.member}"]
                        if (targetNode != null) {
                            createEdge(node, targetNode, EdgeKind.CALLS)
                        }
                    }
                }
            }
        }
    }

    // --- [КОНЕЦ ИСПРАВЛЕНИЯ] ---


    /**
     * Пытаемся угадать FQN ресивера (e.g., "myService").
     */
    private fun resolveReceiverType(
        receiverName: String,
        classFqn: String?,
        fieldsByClassFqn: Map<String, Map<String?, Node>>,
        nodeByFqn: Map<String, Node>,
        nodesBySimpleName: Map<String?, List<Node>>
    ): String? {
        // Попытка 1: Это поле в текущем классе?
        if (classFqn != null) {
            val fieldNode = fieldsByClassFqn[classFqn]?.get(receiverName)
            if (fieldNode != null) {
                // У нас нет типа поля! Читаем "сырой" source code...
                val fieldSource = fieldNode.sourceCode ?: "" // e.g., "val myService: MyService"
                val typeName = fieldSource.substringAfter(':').substringBefore('=').trim()

                if (typeName.isNotEmpty()) {
                    // Теперь надо "угадать" FQN для "MyService" (без импортов!)
                    val pkg = fieldNode.packageName ?: ""
                    // Попытка 1.1: Тип в том же пакете
                    return nodeByFqn["$pkg.$typeName"]?.fqn
                    // Попытка 1.2: Ищем по всей карте простых имен
                        ?: nodesBySimpleName[typeName]?.firstOrNull { it.isTypeNode() }?.fqn
                }
            }
        }

        // Попытка 2: Это статический вызов? (e.g., "Utils.doWork()")
        if (receiverName.length > 0 && receiverName[0].isUpperCase()) {
            // "Utils" -> "com.example.Utils" (первый попавшийся)
            return nodesBySimpleName[receiverName]?.firstOrNull { it.isTypeNode() }?.fqn
        }

        // Попытка 3: Это FQN? (e.g., "com.example.Utils.doWork()")
        if (receiverName.contains('.')) {
            return receiverName // FQN как он есть
        }

        return null // Не смогли угадать
    }

    /** Хелпер для десериализации RawUsage из Map<String, Any> */
    private fun deserializeRawUsage(data: Map<String, Any>): RawUsage? {
        return try {
            if (data.containsKey("receiver")) {
                RawUsage.Dot(
                    receiver = data["receiver"] as String,
                    member = data["member"] as String,
                    isCall = data["isCall"] as Boolean
                )
            } else if (data.containsKey("name")) {
                RawUsage.Simple(
                    name = data["name"] as String,
                    isCall = data["isCall"] as Boolean
                )
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to cast data map to RawUsage. Data: $data", e)
            null // Ошибка каста
        }
    }

    /** Хелпер для безопасного создания ребра (защита от дубликатов) */
    private fun createEdge(src: Node, dst: Node, kind: EdgeKind) {
        // Тут можно добавить `edgeRepo.existsBy...` если нужно,
        // но для N^2 линкера проще положиться на constraint в БД.
        runCatching {
            edgeRepo.save(Edge(src = src, dst = dst, kind = kind))
        }.onFailure {
            // Логируем ошибку, если ребро не создалось (например, дубликат)
            if (it.message?.contains("constraint violation") == false) {
                log.warn(
                    "Failed to create edge ($kind) from [${src.fqn}] to [${dst.fqn}]. Error: ${it.message}"
                )
            }
            // Если это constraint violation (дубликат), то молча игнорируем, всё ок
        }
    }

    // --- Хелперы для NodeKind ---
    private fun Node.isTypeNode() =
        this.kind in setOf(NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.ENUM, NodeKind.RECORD)

    private fun Node.isFunctionNode() =
        this.kind in setOf(NodeKind.METHOD, NodeKind.ENDPOINT, NodeKind.JOB, NodeKind.TOPIC)
}