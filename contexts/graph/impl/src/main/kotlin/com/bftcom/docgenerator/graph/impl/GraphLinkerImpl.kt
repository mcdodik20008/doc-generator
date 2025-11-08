package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.graph.api.GraphLinker
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.domain.node.RawUsage
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.text.orEmpty

@Service
class GraphLinkerImpl(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
    private val objectMapper: ObjectMapper,
) : GraphLinker {
    companion object {
        private val log = LoggerFactory.getLogger(GraphLinker::class.java)

        // очень базовый извлекатель типов из сигнатуры
        private val TYPE_TOKEN = Regex("""\:\s*([A-Za-z_][A-Za-z0-9_\.]*)""")
    }

    @Transactional
    override fun link(application: Application) {
        log.info("Starting graph linking for app [id=${application.id}]...")

        val all = nodeRepo.findAllByApplicationId(application.id!!, Pageable.ofSize(Int.MAX_VALUE))
        log.info("Fetched ${all.size} total nodes to link.")
        if (all.isEmpty()) {
            log.warn("No nodes found; skipping.")
            return
        }

        // индексы
        val byFqn: Map<String, Node> = all.associateBy { it.fqn }
        val bySimple: Map<String?, List<Node>> = all.groupBy { it.name }
        val packages: Map<String?, Node> = all.filter { it.kind == NodeKind.PACKAGE }.associateBy { it.fqn }

        // быстрый доступ к мета (типизировано)
        fun metaOf(n: Node): NodeMeta =
            when (val m = n.meta) {
                else -> objectMapper.convertValue(m, NodeMeta::class.java)
            }

        // 0) CONTAINS (структура)
        linkContains(all, packages, byFqn, ::metaOf)

        var callsErrors = 0

        // 1) проход по нодам
        all.forEachIndexed { i, node ->
            val p = ((i + 1) * 100.0 / all.size).toInt()
            log.info("[${i + 1}/${all.size}, $p%] Linking: ${node.kind} ${node.fqn}")

            val meta = metaOf(node)

            // Наследование/реализация
            if (node.isTypeNode()) {
                linkInheritsImplements(node, meta, byFqn, bySimple)
            }

            // Аннотации
            linkAnnotations(node, meta, byFqn, bySimple)

            // DEPENDS_ON по сигнатурам
            if (node.isFunctionNode()) {
                linkSignatureDepends(node, meta, byFqn, bySimple)
            }

            // CALLS
            if (node.isFunctionNode()) {
                try {
                    linkCalls(node, meta, byFqn, bySimple)
                } catch (e: Exception) {
                    callsErrors++
                    log.error("CALLS linking failed for ${node.fqn}: ${e.message}", e)
                }
            }

            // THROWS
            if (node.isFunctionNode()) {
                try {
                    linkThrows(node, meta, byFqn, bySimple)
                } catch (e: Exception) {
                    log.warn("THROWS linking failed for ${node.fqn}: ${e.message}", e)
                }
            }
        }

        log.info("Finished linking. CALLS errors: $callsErrors")
    }

    /** PACKAGE→TYPE и TYPE→{METHOD,FIELD} */
    private fun linkContains(
        all: List<Node>,
        packages: Map<String?, Node>,
        byFqn: Map<String, Node>,
        metaOf: (Node) -> NodeMeta,
    ) {
        // PACKAGE -> TYPE
        all
            .asSequence()
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
                val pkg = packages[type.packageName] ?: return@forEach
                upsertEdge(pkg, type, EdgeKind.CONTAINS)
            }

        // TYPE -> METHOD/FIELD
        all
            .asSequence()
            .filter {
                it.kind == NodeKind.METHOD || it.kind == NodeKind.FIELD || it.kind == NodeKind.ENDPOINT || it.kind == NodeKind.JOB ||
                    it.kind == NodeKind.TOPIC
            }.forEach { member ->
                val ownerFqn = metaOf(member).ownerFqn
                val owner = ownerFqn?.let { byFqn[it] } ?: return@forEach
                upsertEdge(owner, member, EdgeKind.CONTAINS)
            }
    }

    /** INHERITS vs IMPLEMENTS (и опц. DEPENDS_ON на тот же тип) */
    private fun linkInheritsImplements(
        node: Node,
        meta: NodeMeta,
        byFqn: Map<String, Node>,
        bySimple: Map<String?, List<Node>>,
    ) {
        val imports = meta.imports ?: emptyList()
        val pkg = node.packageName.orEmpty()

        // приоритет готовым FQN, иначе — supertypesSimple + imports/pkg
        val candidates =
            (meta.supertypesResolved ?: emptyList()) +
                (meta.supertypesSimple ?: emptyList())

        for (raw in candidates) {
            val simple = raw.substringAfterLast('.').removeSuffix("?").substringBefore('<')
            val target =
                resolveType(simpleOrFqn = raw, imports = imports, pkg = pkg, byFqn = byFqn, bySimple = bySimple)
                    ?: continue

            when (target.kind) {
                NodeKind.INTERFACE -> {
                    upsertEdge(node, target, EdgeKind.IMPLEMENTS)
                    upsertEdge(node, target, EdgeKind.DEPENDS_ON) // держим и общую зависимость
                }

                else -> {
                    upsertEdge(node, target, EdgeKind.INHERITS)
                    upsertEdge(node, target, EdgeKind.DEPENDS_ON)
                }
            }
        }
    }

    /** ANNOTATED_WITH (+ DEPENDS_ON) */
    private fun linkAnnotations(
        node: Node,
        meta: NodeMeta,
        byFqn: Map<String, Node>,
        bySimple: Map<String?, List<Node>>,
    ) {
        val annotations = meta.annotations ?: return
        if (annotations.isEmpty()) return

        val imports = meta.imports ?: emptyList()
        val pkg = node.packageName.orEmpty()

        for (a in annotations) {
            val t = resolveType(a, imports, pkg, byFqn, bySimple) ?: continue
            upsertEdge(node, t, EdgeKind.ANNOTATED_WITH)
            upsertEdge(node, t, EdgeKind.DEPENDS_ON)
        }
    }

    /** DEPENDS_ON из типов сигнатуры (paramTypes/returnType или грубый парсер по signature) */
    private fun linkSignatureDepends(
        fn: Node,
        meta: NodeMeta,
        byFqn: Map<String, Node>,
        bySimple: Map<String?, List<Node>>,
    ) {
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
        val src = ownerFqn?.let { byFqn[it] } ?: fn

        for (t in tokens) {
            val typeNode = resolveType(t, imports, pkg, byFqn, bySimple) ?: continue
            if (typeNode.id != src.id) upsertEdge(src, typeNode, EdgeKind.DEPENDS_ON)
        }
    }

    /** CALLS: method→method (Simple/Dot) + конструктор как CALLS на тип-владельца */
    private fun linkCalls(
        fn: Node,
        meta: NodeMeta,
        byFqn: Map<String, Node>,
        bySimple: Map<String?, List<Node>>,
    ) {
        val usages = meta.rawUsages ?: return
        if (usages.isEmpty()) return

        val imports = meta.imports ?: emptyList()
        val ownerFqn = meta.ownerFqn
        val owner = ownerFqn?.let { byFqn[it] }
        val pkg = fn.packageName.orEmpty()

        usages.forEach { u ->
            when (u) {
                is RawUsage.Simple -> {
                    // локальный метод
                    if (ownerFqn != null) {
                        byFqn["$ownerFqn.${u.name}"]?.let {
                            upsertEdge(fn, it, EdgeKind.CALLS)
                            return@forEach
                        }
                    }
                    // конструктор Type(...)
                    if (u.isCall) {
                        val t = resolveType(u.name, imports, pkg, byFqn, bySimple) ?: return@forEach
                        upsertEdge(fn, t, EdgeKind.CALLS)
                    }
                }

                is RawUsage.Dot -> {
                    // попытка вывести тип получателя по имени:
                    // 1) если Receiver начинается с заглавной — вероятно тип
                    val recvType: Node? =
                        if (u.receiver.firstOrNull()?.isUpperCase() == true) {
                            resolveType(u.receiver, imports, pkg, byFqn, bySimple)
                        } else {
                            // 2) иначе пытаемся найти тип по FQN "owner.fieldName" или по simple имени
                            // (для базовой версии — пробуем owner.fieldMethod)
                            // Если известен владелец — пробуем <OwnerType>.<member>
                            val inferredOwner = owner
                            when {
                                inferredOwner != null ->
                                    byFqn["${inferredOwner.fqn}.${u.member}"]?.let {
                                        return@forEach upsertEdge(
                                            fn,
                                            it,
                                            EdgeKind.CALLS,
                                        )
                                    }

                                else -> null
                            }
                            null
                        }

                    // если нашли тип, пробуем метод у этого типа
                    if (recvType != null) {
                        byFqn["${recvType.fqn}.${u.member}"]?.let { upsertEdge(fn, it, EdgeKind.CALLS) }
                    }
                }
            }
        }
    }

    // ------------------------ helpers ------------------------

    private fun resolveType(
        simpleOrFqn: String,
        imports: List<String>,
        pkg: String,
        byFqn: Map<String, Node>,
        bySimple: Map<String?, List<Node>>,
    ): Node? {
        // точный FQN
        byFqn[simpleOrFqn]?.let { return it }

        val simple = simpleOrFqn.substringAfterLast('.').removeSuffix("?").substringBefore('<')

        // import ....<simple>
        imports.firstOrNull { it.endsWith(".$simple") }?.let { byFqn[it] }?.let { return it }

        // тот же пакет
        byFqn["$pkg.$simple"]?.let { return it }

        // по простому имени (последним приоритетом)
        return bySimple[simple]?.firstOrNull()
    }

    private fun upsertEdge(
        src: Node,
        dst: Node,
        kind: EdgeKind,
    ) {
        if (src.id == null || dst.id == null) return
        edgeRepo.upsert(src.id!!, dst.id!!, kind.name)
    }

    private fun Node.isTypeNode(): Boolean =
        this.kind in
            setOf(
                NodeKind.CLASS,
                NodeKind.INTERFACE,
                NodeKind.ENUM,
                NodeKind.RECORD,
                NodeKind.SERVICE,
                NodeKind.MAPPER,
                NodeKind.CONFIG,
            )

    /** THROWS: method→exception type */
    private fun linkThrows(
        fn: Node,
        meta: NodeMeta,
        byFqn: Map<String, Node>,
        bySimple: Map<String?, List<Node>>,
    ) {
        val throwsTypes = meta.throwsTypes ?: return
        if (throwsTypes.isEmpty()) {
            return
        }

        val imports = meta.imports ?: emptyList()
        val pkg = fn.packageName.orEmpty()

        throwsTypes.forEach { throwType ->
            // Разрешаем тип исключения
            val exceptionNode = resolveType(throwType, imports, pkg, byFqn, bySimple)
            if (exceptionNode != null) {
                upsertEdge(fn, exceptionNode, EdgeKind.THROWS)
            } else {
                log.debug("Could not resolve exception type '$throwType' for ${fn.fqn}")
            }
        }
    }

    private fun Node.isFunctionNode(): Boolean = this.kind in setOf(NodeKind.METHOD, NodeKind.ENDPOINT, NodeKind.JOB, NodeKind.TOPIC)
}
