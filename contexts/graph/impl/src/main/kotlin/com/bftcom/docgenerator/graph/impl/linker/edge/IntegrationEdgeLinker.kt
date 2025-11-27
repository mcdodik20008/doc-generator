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
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import org.springframework.stereotype.Component

/**
 * Линкер для интеграционных связей (HTTP, Kafka, Camel).
 * Создаёт связи CALLS_HTTP, PRODUCES, CONSUMES на основе LibraryNode.
 * Также создаёт виртуальные узлы (ENDPOINT, TOPIC) и прямые связи с LibraryNode.
 */
@Component
class IntegrationEdgeLinker(
    private val libraryNodeIndex: LibraryNodeIndex,
    private val integrationPointService: IntegrationPointService,
    private val virtualNodeFactory: VirtualNodeFactory,
) : EdgeLinker {
    override fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>> {
        // Этот линкер требует дополнительных параметров (Application),
        // поэтому возвращает пустой список из базового метода.
        // Реальная логика вызывается через linkIntegrationEdgesWithNodes.
        return emptyList()
    }

    /**
     * Создаёт интеграционные рёбра с дополнительной информацией.
     * @return Тройка: (список рёбер между Node, список новых созданных узлов, список рёбер между Node и LibraryNode)
     */
    fun linkIntegrationEdgesWithNodes(
        fn: Node,
        meta: NodeMeta,
        index: NodeIndex,
        application: Application,
    ): Triple<List<Triple<Node, Node, EdgeKind>>, List<Node>, List<LibraryNodeEdgeProposal>> {
        val res = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val newNodes = mutableListOf<Node>()
        val libraryNodeEdges = mutableListOf<LibraryNodeEdgeProposal>()
        val usages = meta.rawUsages ?: return Triple(emptyList(), emptyList(), emptyList())
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
                    // Создаем прямую связь с LibraryNode
                    libraryNodeEdges.add(
                        LibraryNodeEdgeProposal(
                            kind = EdgeKind.CALLS_CODE,
                            node = fn,
                            libraryNode = libraryNode,
                        ),
                    )

                    // Нашли метод в библиотеке - извлекаем интеграционные точки
                    val integrationPoints = integrationPointService.extractIntegrationPoints(libraryNode)

                    for (point in integrationPoints) {
                        when (point) {
                            is com.bftcom.docgenerator.library.api.integration.IntegrationPoint.HttpEndpoint -> {
                                // Создаем прямую связь с LibraryNode для HTTP
                                libraryNodeEdges.add(
                                    LibraryNodeEdgeProposal(
                                        kind = EdgeKind.CALLS_HTTP,
                                        node = fn,
                                        libraryNode = libraryNode,
                                    ),
                                )

                                // Создаем или находим узел ENDPOINT (для обратной совместимости)
                                val (endpointNode, isNew) =
                                    virtualNodeFactory.getOrCreateEndpointNode(
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
                                        libraryNodeEdges.add(
                                            LibraryNodeEdgeProposal(
                                                kind = EdgeKind.RETRIES_TO,
                                                node = fn,
                                                libraryNode = libraryNode,
                                            ),
                                        )
                                    }
                                    if (point.hasTimeout) {
                                        res += Triple(fn, endpointNode, EdgeKind.TIMEOUTS_TO)
                                        libraryNodeEdges.add(
                                            LibraryNodeEdgeProposal(
                                                kind = EdgeKind.TIMEOUTS_TO,
                                                node = fn,
                                                libraryNode = libraryNode,
                                            ),
                                        )
                                    }
                                    if (point.hasCircuitBreaker) {
                                        res += Triple(fn, endpointNode, EdgeKind.CIRCUIT_BREAKER_TO)
                                        libraryNodeEdges.add(
                                            LibraryNodeEdgeProposal(
                                                kind = EdgeKind.CIRCUIT_BREAKER_TO,
                                                node = fn,
                                                libraryNode = libraryNode,
                                            ),
                                        )
                                    }
                                }
                            }
                            is com.bftcom.docgenerator.library.api.integration.IntegrationPoint.KafkaTopic -> {
                                // Создаем прямую связь с LibraryNode для Kafka
                                libraryNodeEdges.add(
                                    LibraryNodeEdgeProposal(
                                        kind = if (point.operation == "PRODUCE") EdgeKind.PRODUCES else EdgeKind.CONSUMES,
                                        node = fn,
                                        libraryNode = libraryNode,
                                    ),
                                )

                                // Создаем или находим узел TOPIC (для обратной совместимости)
                                val (topicNode, isNew) =
                                    virtualNodeFactory.getOrCreateTopicNode(
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
                                // Создаем прямую связь с LibraryNode для Camel
                                if (point.endpointType == "http" || point.uri?.startsWith("http") == true) {
                                    libraryNodeEdges.add(
                                        LibraryNodeEdgeProposal(
                                            kind = EdgeKind.CALLS_HTTP,
                                            node = fn,
                                            libraryNode = libraryNode,
                                        ),
                                    )
                                }

                                // Для Camel создаем ENDPOINT узел (для обратной совместимости)
                                val (endpointNode, isNew) =
                                    virtualNodeFactory.getOrCreateEndpointNode(
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

        return Triple(res, newNodes, libraryNodeEdges)
    }
}

