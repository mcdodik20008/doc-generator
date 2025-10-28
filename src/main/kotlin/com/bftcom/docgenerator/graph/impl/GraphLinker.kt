package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.domain.application.Application
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

@Service
class GraphLinker(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(GraphLinker::class.java)
    }

    @Transactional
    fun link(application: Application) {
        log.info("Starting graph linking for app [id=${application.id}]...")

        val allNodes = nodeRepo.findAllByApplicationId(application.id!!, Pageable.ofSize(Int.MAX_VALUE))
        log.info("Fetched ${allNodes.size} total nodes to link.")
        if (allNodes.isEmpty()) {
            log.warn("No nodes found for application [id=${application.id}]. Linking skipped.")
            return
        }

        val nodeByFqn = allNodes.associateBy { it.fqn }
        val nodesBySimpleName = allNodes.groupBy { it.name }

        // ClassFQN -> (fieldName -> fieldNode)
        @Suppress("UNCHECKED_CAST")
        val fieldsByClassFqn: Map<String, Map<String?, Node>> =
            allNodes
                .asSequence()
                .filter { it.kind == NodeKind.FIELD }
                .groupBy { it.meta["ownerFqn"] as? String }
                .filterKeys { it != null }
                .mapValues { (_, fields) -> fields.associateBy { it.name } }
                as Map<String, Map<String?, Node>>

        var linkCallsErrors = 0
        val totalNodes = allNodes.size
        log.info("Starting dependency linking for $totalNodes nodes...")

        // 2. Заменяем for-loop на forEachIndexed
        allNodes.forEachIndexed { index, node ->

            // 3. Добавляем логгирование прогресса (как в вашем примере)
            val percent = if (totalNodes > 0) ((index + 1) * 100.0 / totalNodes).toInt() else 100
            log.info("[${index + 1}/$totalNodes, $percent%] Linking node: ${node.fqn}")

            // 4. Ваша оригинальная логика из тела цикла
            if (node.isTypeNode()) {
                linkInheritance(node, nodeByFqn, nodesBySimpleName)
                linkFieldTypeDependencies(node, fieldsByClassFqn, nodeByFqn, nodesBySimpleName)
            }
            if (node.isFunctionNode()) {
                try {
                    linkCalls(node, nodeByFqn, nodesBySimpleName, fieldsByClassFqn)
                    linkSignatureTypeDependencies(node, nodeByFqn, nodesBySimpleName)
                } catch (e: Exception) {
                    log.error("Failed to link calls for node [${node.fqn}]: ${e.message}", e)
                    linkCallsErrors++
                }
            }
        }

        // 5. Опционально: лог о завершении
        log.info("Finished linking nodes. Total 'linkCalls' errors: $linkCallsErrors")
    }

    private fun linkInheritance(
        node: Node,
        nodeByFqn: Map<String, Node>,
        nodesBySimpleName: Map<String?, List<Node>>,
    ) {
        val supertypes = node.meta["supertypesSimple"] as? List<*> ?: return
        val imports = (node.meta["imports"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val pkgFqn = node.packageName ?: return

        supertypes.filterIsInstance<String>().forEach { superName ->
            val simple = superName.removeSuffix("?").substringBefore('<')
            val target =
                imports.firstOrNull { it.endsWith(".$simple") }?.let { nodeByFqn[it] }
                    ?: nodeByFqn["$pkgFqn.$simple"]
                    ?: nodesBySimpleName[simple]?.firstOrNull { it.isTypeNode() }

            target?.let {
                val kind = if (it.kind == NodeKind.INTERFACE) EdgeKind.IMPLEMENTS else EdgeKind.DEPENDS_ON
                createEdge(node, it, kind)
            }
        }
    }

    private fun linkFieldTypeDependencies(
        typeNode: Node, // нода КЛАССА/ИНТЕРФЕЙСА/ENUM/RECORD
        fieldsByClassFqn: Map<String, Map<String?, Node>>,
        nodeByFqn: Map<String, Node>,
        nodesBySimpleName: Map<String?, List<Node>>,
    ) {
        val classFqn = typeNode.fqn ?: return
        val fields = fieldsByClassFqn[classFqn] ?: return
        val imports = (typeNode.meta["imports"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val pkg = typeNode.packageName.orEmpty()

        fields.values.forEach { fieldNode ->
            val typeSimpleRaw = fieldNode.meta["typeSimple"] as? String ?: return@forEach
            val simple = typeSimpleRaw.removeSuffix("?").substringBefore('<').trim()
            if (simple.isEmpty()) return@forEach

            // 1) по импортам
            val fqnByImport = imports.firstOrNull { it.endsWith(".$simple") }
            val target =
                when {
                    fqnByImport != null -> nodeByFqn[fqnByImport]
                    else -> nodeByFqn["$pkg.$simple"] ?: nodesBySimpleName[simple]?.firstOrNull { it.isTypeNode() }
                }
            if (target != null) {
                createEdge(typeNode, target, EdgeKind.DEPENDS_ON)
            }
        }
    }

    private fun linkCalls(
        node: Node,
        nodeByFqn: Map<String, Node>,
        nodesBySimpleName: Map<String?, List<Node>>,
        fieldsByClassFqn: Map<String, Map<String?, Node>>,
    ) {
        val rawList = node.meta["rawUsages"] as? List<*> ?: return

        val usages: List<RawUsage> =
            rawList.mapNotNull { item ->
                when (item) {
                    is RawUsage -> item
                    is Map<*, *> -> deserializeRawUsageSafe(item)
                    else -> null
                }
            }

        val classFqn = node.meta["ownerFqn"] as? String
        val imports = (node.meta["imports"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        usages.forEach { usage ->
            when (usage) {
                is RawUsage.Simple -> {
                    // локальный метод того же класса
                    val own = classFqn?.let { nodeByFqn["$it.${usage.name}"] }
                    if (own != null) {
                        createEdge(node, own, EdgeKind.CALLS)
                        return@forEach
                    }
                    // конструктор Type(...)
                    if (usage.isCall) {
                        val simple = usage.name
                        val owner =
                            imports.firstOrNull { it.endsWith(".$simple") }?.let { nodeByFqn[it] }
                                ?: nodesBySimpleName[simple]?.firstOrNull { it.isTypeNode() }
                        owner?.let {
                            // Хотите — заведите EdgeKind.INSTANTIATES. Пока CALLS к владельцу.
                            createEdge(node, it, EdgeKind.CALLS)
                        }
                    }
                }
                is RawUsage.Dot -> {
                    val receiverTypeFqn =
                        resolveReceiverType(
                            receiverName = usage.receiver,
                            classFqn = classFqn,
                            fieldsByClassFqn = fieldsByClassFqn,
                            nodeByFqn = nodeByFqn,
                            nodesBySimpleName = nodesBySimpleName,
                            imports = imports,
                        )
                    receiverTypeFqn?.let { typeFqn ->
                        nodeByFqn["$typeFqn.${usage.member}"]?.let { target ->
                            createEdge(node, target, EdgeKind.CALLS)
                        }
                    }
                }
            }
        }
    }

    private fun linkSignatureTypeDependencies(
        fnNode: Node,
        nodeByFqn: Map<String, Node>,
        nodesBySimpleName: Map<String?, List<Node>>,
    ) {
        val sig = fnNode.signature ?: return
        val ownerFqn = fnNode.meta["ownerFqn"] as? String
        val ownerNode = ownerFqn?.let { nodeByFqn[it] }
        val imports = (fnNode.meta["imports"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val pkg = fnNode.packageName.orEmpty()

        // очень простая выборка типов из сигнатуры: ищем токены после ':' и до ',', ')' или '='
        // покрывает параметры и возврат. Мы берём ТОЛЬКО простое имя до '<'
        val typeTokens =
            Regex("""\:\s*([A-Za-z_][A-Za-z0-9_\.]*)""")
                .findAll(sig)
                .map { it.groupValues[1].substringBefore('<').substringBefore('?') }
                .filter { it.isNotBlank() }
                .toSet()

        for (simpleOrFqn in typeTokens) {
            val simple = simpleOrFqn.substringAfterLast('.')
            val target =
                // если уже FQN и у нас есть такая нода
                nodeByFqn[simpleOrFqn]
                    ?: imports.firstOrNull { it.endsWith(".$simple") }?.let { nodeByFqn[it] }
                    ?: nodeByFqn["$pkg.$simple"]
                    ?: nodesBySimpleName[simple]?.firstOrNull { it.isTypeNode() }

            // привязываем зависимость на уровне класса-владельца, если он есть; иначе — на функцию
            val src = ownerNode ?: fnNode
            if (target != null && src.id != target.id) {
                createEdge(src, target, EdgeKind.DEPENDS_ON)
            }
        }
    }

    private fun resolveReceiverType(
        receiverName: String,
        classFqn: String?,
        fieldsByClassFqn: Map<String, Map<String?, Node>>,
        nodeByFqn: Map<String, Node>,
        nodesBySimpleName: Map<String?, List<Node>>,
        imports: List<String>,
    ): String? {
        // поле текущего класса?
        if (classFqn != null) {
            val fieldNode = fieldsByClassFqn[classFqn]?.get(receiverName)
            val typeSimple = fieldNode?.meta?.get("typeSimple") as? String
            if (!typeSimple.isNullOrBlank()) {
                val simple = typeSimple.removeSuffix("?").substringBefore('<')
                imports.firstOrNull { it.endsWith(".$simple") }?.let { return it }
                val pkg = fieldNode.packageName.orEmpty()
                nodeByFqn["$pkg.$simple"]?.let { return it.fqn }
                nodesBySimpleName[simple]?.firstOrNull { it.isTypeNode() }?.let { return it.fqn }
            }
        }

        // статический/объектный вызов: "Utils.doWork"
        if (receiverName.firstOrNull()?.isUpperCase() == true) {
            nodesBySimpleName[receiverName]?.firstOrNull { it.isTypeNode() }?.let { return it.fqn }
        }

        // уже FQN
        if (receiverName.contains('.')) return receiverName
        return null
    }

    private fun deserializeRawUsageSafe(data: Map<*, *>): RawUsage? =
        try {
            @Suppress("UNCHECKED_CAST")
            val m = data as Map<String, Any>
            if (m.containsKey("receiver")) {
                RawUsage.Dot(
                    receiver = m["receiver"] as String,
                    member = m["member"] as String,
                    isCall = m["isCall"] as Boolean,
                )
            } else if (m.containsKey("name")) {
                RawUsage.Simple(
                    name = m["name"] as String,
                    isCall = m["isCall"] as Boolean,
                )
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to deserialize RawUsage map: $data", e)
            null
        }

    private fun createEdge(
        src: Node,
        dst: Node,
        kind: EdgeKind,
    ) {
        // быстрый upsert без исключений
        edgeRepo.upsert(src.id!!, dst.id!!, kind.name)
    }

    private fun Node.isTypeNode() = this.kind in setOf(NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.ENUM, NodeKind.RECORD)

    private fun Node.isFunctionNode() = this.kind in setOf(NodeKind.METHOD, NodeKind.ENDPOINT, NodeKind.JOB, NodeKind.TOPIC)
}
