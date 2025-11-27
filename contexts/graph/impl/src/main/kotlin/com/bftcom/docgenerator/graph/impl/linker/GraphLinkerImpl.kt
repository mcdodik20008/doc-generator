package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.graph.api.library.LibraryNodeIndex
import com.bftcom.docgenerator.graph.api.linker.GraphLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import com.bftcom.docgenerator.graph.api.linker.sink.GraphSink
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeGraphSink
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeEdgeProposal
import com.bftcom.docgenerator.graph.impl.linker.edge.AnnotationEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.CallEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.InheritanceEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.IntegrationEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.SignatureDependencyLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.StructuralEdgeLinker
import com.bftcom.docgenerator.graph.impl.linker.edge.ThrowEdgeLinker
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Сервис для линковки узлов графа - создания рёбер между узлами.
 * Оркестрирует работу различных линкеров через Strategy Pattern.
 */
@Service
class GraphLinkerImpl(
    private val nodeRepo: NodeRepository,
    private val nodeIndexFactory: NodeIndexFactory,
    private val sink: GraphSink,
    private val libraryNodeSink: LibraryNodeGraphSink,
    private val objectMapper: ObjectMapper,
    // Линкеры для различных типов связей
    private val structuralEdgeLinker: StructuralEdgeLinker,
    private val inheritanceEdgeLinker: InheritanceEdgeLinker,
    private val annotationEdgeLinker: AnnotationEdgeLinker,
    private val signatureDependencyLinker: SignatureDependencyLinker,
    private val callEdgeLinker: CallEdgeLinker,
    private val throwEdgeLinker: ThrowEdgeLinker,
    private val integrationEdgeLinker: IntegrationEdgeLinker,
) : GraphLinker {
    companion object {
        private val log = LoggerFactory.getLogger(GraphLinker::class.java)
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
        val libraryNodeEdges = mutableListOf<LibraryNodeEdgeProposal>()
        val newlyCreatedNodes = mutableListOf<Node>()

        // === STRUCTURE ===
        edges += structuralEdgeLinker.linkContains(all, index, ::metaOf)

        var callsErrors = 0
        all.forEachIndexed { i, node ->
            val p = ((i + 1) * 100.0 / all.size).toInt()
            log.info("[${i + 1}/${all.size}, $p%] Linking: ${node.kind} ${node.fqn}")

            val meta = metaOf(node)
            if (node.isTypeNode()) {
                edges += inheritanceEdgeLinker.link(node, meta, index)
            }
            edges += annotationEdgeLinker.link(node, meta, index)
            if (node.isFunctionNode()) {
                edges += signatureDependencyLinker.link(node, meta, index)
                try {
                    edges += callEdgeLinker.link(node, meta, index)
                    // Создаем интеграционные Edge на основе LibraryNode
                    // Собираем новые узлы для обновления индекса
                    val (integrationEdges, newNodes, libEdges) =
                        integrationEdgeLinker.linkIntegrationEdgesWithNodes(node, meta, index, application)
                    edges += integrationEdges
                    newlyCreatedNodes += newNodes
                    libraryNodeEdges += libEdges
                } catch (e: Exception) {
                    callsErrors++
                    log.error("CALLS linking failed for ${node.fqn}: ${e.message}", e)
                }
                edges += throwEdgeLinker.link(node, meta, index)
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

        // Сохраняем прямые связи с LibraryNode
        if (libraryNodeEdges.isNotEmpty()) {
            log.info("Saving ${libraryNodeEdges.size} direct links to library nodes")
            libraryNodeSink.upsertLibraryNodeEdges(libraryNodeEdges.asSequence())
        }

        log.info("Finished linking. CALLS errors: $callsErrors, new integration nodes: ${newlyCreatedNodes.size}, library node edges: ${libraryNodeEdges.size}")
    }

    // ================= helpers =================

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
}
