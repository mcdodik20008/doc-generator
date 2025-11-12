package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.domain.node.RawUsage
import com.bftcom.docgenerator.graph.api.linker.GraphLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import com.bftcom.docgenerator.graph.api.linker.sink.GraphSink
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GraphLinkerImpl(
    private val nodeRepo: NodeRepository,
    private val nodeIndexFactory: NodeIndexFactory,
    private val sink: GraphSink,
    private val objectMapper: ObjectMapper,
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

        val index = nodeIndexFactory.create(all)

        fun metaOf(n: Node): NodeMeta = objectMapper.convertValue(n.meta, NodeMeta::class.java)

        val edges = mutableListOf<Triple<Node, Node, EdgeKind>>()

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
                } catch (e: Exception) {
                    callsErrors++
                    log.error("CALLS linking failed for ${node.fqn}: ${e.message}", e)
                }
                edges += linkThrows(node, meta, index)
            }
        }

        // === persist ===
        sink.upsertEdges(edges.asSequence().map { (src, dst, kind) ->
            SimpleEdgeProposal(kind, src, dst)
        })

        log.info("Finished linking. CALLS errors: $callsErrors")
    }

    // ================= helpers =================

    private fun linkContains(
        all: List<Node>,
        index: NodeIndex,
        metaOf: (Node) -> NodeMeta
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()

        all.filter {
            it.kind in setOf(
                NodeKind.INTERFACE, NodeKind.SERVICE, NodeKind.RECORD,
                NodeKind.MAPPER, NodeKind.ENDPOINT, NodeKind.CLASS,
                NodeKind.ENUM, NodeKind.CONFIG
            )
        }.forEach { type ->
            val pkg = index.findByFqn(type.packageName ?: return@forEach) ?: return@forEach
            res += Triple(pkg, type, EdgeKind.CONTAINS)
        }

        all.filter {
            it.kind in setOf(
                NodeKind.METHOD, NodeKind.FIELD, NodeKind.ENDPOINT,
                NodeKind.JOB, NodeKind.TOPIC
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
        index: NodeIndex
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
        index: NodeIndex
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
        index: NodeIndex
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val imports = meta.imports ?: emptyList()
        val pkg = fn.packageName.orEmpty()

        val tokens: Set<String> =
            when {
                !meta.paramTypes.isNullOrEmpty() || !meta.returnType.isNullOrBlank() ->
                    (meta.paramTypes.orEmpty() + listOfNotNull(meta.returnType)).toSet()
                !fn.signature.isNullOrBlank() ->
                    TYPE_TOKEN.findAll(fn.signature!!)
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
        index: NodeIndex
    ): List<Triple<Node, Node, EdgeKind>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val usages = meta.rawUsages ?: return emptyList()
        val imports = meta.imports ?: emptyList()
        val owner = meta.ownerFqn?.let { index.findByFqn(it) }
        val pkg = fn.packageName.orEmpty()

        usages.forEach { u ->
            when (u) {
                is RawUsage.Simple -> {
                    if (owner != null)
                        index.findByFqn("${owner.fqn}.${u.name}")?.let {
                            res += Triple(fn, it, EdgeKind.CALLS)
                            return@forEach
                        }
                    if (u.isCall) {
                        index.resolveType(u.name, imports, pkg)?.let {
                            res += Triple(fn, it, EdgeKind.CALLS)
                        }
                    }
                }
                is RawUsage.Dot -> {
                    val recvType = if (u.receiver.firstOrNull()?.isUpperCase() == true) {
                        index.resolveType(u.receiver, imports, pkg)
                    } else owner
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
        index: NodeIndex
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

    private fun Node.isTypeNode(): Boolean = kind in setOf(
        NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.ENUM, NodeKind.RECORD,
        NodeKind.SERVICE, NodeKind.MAPPER, NodeKind.CONFIG
    )

    private fun Node.isFunctionNode(): Boolean =
        kind in setOf(NodeKind.METHOD, NodeKind.ENDPOINT, NodeKind.JOB, NodeKind.TOPIC)
}
