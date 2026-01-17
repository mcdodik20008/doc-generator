package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.domain.node.RawUsage
import com.bftcom.docgenerator.graph.api.library.LibraryNodeIndex
import com.bftcom.docgenerator.graph.api.linker.EdgeLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeEdgeProposal
import com.bftcom.docgenerator.graph.impl.linker.virtual.VirtualNodeFactory
import com.bftcom.docgenerator.library.api.integration.IntegrationPoint
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import org.springframework.stereotype.Component

@Component
class IntegrationEdgeLinker(
    private val libraryNodeIndex: LibraryNodeIndex,
    private val integrationPointService: IntegrationPointService,
    private val virtualNodeFactory: VirtualNodeFactory,
) : EdgeLinker {

    override fun link(node: Node, meta: NodeMeta, index: NodeIndex) = emptyList<Triple<Node, Node, EdgeKind>>()

    fun linkIntegrationEdgesWithNodes(
        fn: Node,
        meta: NodeMeta,
        index: NodeIndex,
        application: Application,
    ): Triple<List<Triple<Node, Node, EdgeKind>>, List<Node>, List<LibraryNodeEdgeProposal>> {

        val usages = meta.rawUsages ?: return Triple(emptyList(), emptyList(), emptyList())

        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val newNodes = mutableListOf<Node>()
        val libProposals = mutableListOf<LibraryNodeEdgeProposal>()

        usages.forEach { usage ->
            val fqn = resolveLibraryFqn(usage, meta, index) ?: return@forEach
            val libNode = libraryNodeIndex.findByMethodFqn(fqn) ?: return@forEach

            // Базовый вызов pkg
            libProposals.add(LibraryNodeEdgeProposal(EdgeKind.CALLS_CODE, fn, libNode))

            integrationPointService.extractIntegrationPoints(libNode).forEach { point ->
                when (point) {
                    is IntegrationPoint.HttpEndpoint -> handleHttp(fn, point, index, application, res, newNodes, libProposals, libNode)
                    is IntegrationPoint.KafkaTopic -> handleKafka(fn, point, index, application, res, newNodes, libProposals, libNode)
                    is IntegrationPoint.CamelRoute -> handleCamel(fn, point, index, application, res, newNodes, libProposals, libNode)
                }
            }
        }

        return Triple(res, newNodes, libProposals)
    }

    private fun handleHttp(src: Node, pt: IntegrationPoint.HttpEndpoint, idx: NodeIndex, app: Application,
                           res: MutableList<Triple<Node, Node, EdgeKind>>, newNodes: MutableList<Node>,
                           props: MutableList<LibraryNodeEdgeProposal>, lib: com.bftcom.docgenerator.domain.library.LibraryNode) {

        val (node, isNew) = virtualNodeFactory.getOrCreateEndpointNode(pt.url ?: "unknown", pt.httpMethod, idx, app)
        if (node == null) return
        if (isNew) newNodes.add(node)

        // Связываем
        fun addEdge(kind: EdgeKind) {
            res.add(Triple(src, node, kind))
            props.add(LibraryNodeEdgeProposal(kind, src, lib))
        }

        addEdge(EdgeKind.CALLS_HTTP)
        if (pt.hasRetry) addEdge(EdgeKind.RETRIES_TO)
        if (pt.hasTimeout) addEdge(EdgeKind.TIMEOUTS_TO)
        if (pt.hasCircuitBreaker) addEdge(EdgeKind.CIRCUIT_BREAKER_TO)
    }

    private fun handleKafka(src: Node, pt: IntegrationPoint.KafkaTopic, idx: NodeIndex, app: Application,
                            res: MutableList<Triple<Node, Node, EdgeKind>>, newNodes: MutableList<Node>,
                            props: MutableList<LibraryNodeEdgeProposal>, lib: com.bftcom.docgenerator.domain.library.LibraryNode) {

        val (node, isNew) = virtualNodeFactory.getOrCreateTopicNode(pt.topic ?: "unknown", idx, app)
        if (node == null) return
        if (isNew) newNodes.add(node)

        val kind = if (pt.operation == "PRODUCE") EdgeKind.PRODUCES else EdgeKind.CONSUMES
        res.add(Triple(src, node, kind))
        props.add(LibraryNodeEdgeProposal(kind, src, lib))
    }

    private fun handleCamel(src: Node, pt: IntegrationPoint.CamelRoute, idx: NodeIndex, app: Application,
                            res: MutableList<Triple<Node, Node, EdgeKind>>, newNodes: MutableList<Node>,
                            props: MutableList<LibraryNodeEdgeProposal>, lib: com.bftcom.docgenerator.domain.library.LibraryNode) {

        val isHttp = pt.endpointType == "http" || pt.uri?.startsWith("http") == true
        val (node, isNew) = virtualNodeFactory.getOrCreateEndpointNode(pt.uri ?: "unknown", null, idx, app)

        if (node != null) {
            if (isNew) newNodes.add(node)
            if (isHttp) {
                res.add(Triple(src, node, EdgeKind.CALLS_HTTP))
                props.add(LibraryNodeEdgeProposal(EdgeKind.CALLS_HTTP, src, lib))
            }
        }
    }

    private fun resolveLibraryFqn(u: RawUsage, meta: NodeMeta, index: NodeIndex): String? {
        val imports = meta.imports ?: emptyList()
        val ownerFqn = meta.ownerFqn

        return when (u) {
            is RawUsage.Simple -> {
                ownerFqn?.let { "$it.${u.name}" }
                    ?: imports.find { it.endsWith(".${u.name}") } // Static import
                    ?: imports.find { it.split('.').last().firstOrNull()?.isUpperCase() == true }?.let { "$it.${u.name}" } // Class import
                    ?: if (u.name.contains('.')) u.name else null
            }
            is RawUsage.Dot -> {
                val recvType = if (u.receiver.firstOrNull()?.isUpperCase() == true) {
                    index.resolveType(u.receiver, imports, meta.pkgFqn ?: "")?.fqn
                } else ownerFqn
                recvType?.let { "$it.${u.member}" }
            }
        }
    }
}