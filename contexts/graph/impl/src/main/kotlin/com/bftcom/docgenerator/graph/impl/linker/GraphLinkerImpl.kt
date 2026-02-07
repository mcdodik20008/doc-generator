package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.graph.api.linker.GraphLinker
import com.bftcom.docgenerator.graph.api.linker.indexing.NodeIndex
import com.bftcom.docgenerator.graph.api.linker.sink.GraphSink
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeEdgeProposal
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeGraphSink
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
import org.springframework.beans.factory.annotation.Value
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Сервис для линковки узлов графа - создания рёбер между узлами. Оркестрирует работу различных
 * линкеров через Strategy Pattern.
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
        @Value("\${docgen.graph.max-nodes:100000}")
        private val maxNodesToLoad: Int,
) : GraphLinker {
    companion object {
        private val log = LoggerFactory.getLogger(GraphLinker::class.java)
        private const val PARALLEL_LINKING_TIMEOUT_MINUTES = 10L
        private const val MAX_ERROR_THRESHOLD = 100
    }

    @Transactional
    override fun link(application: Application) {
        val startTime = System.currentTimeMillis()
        val runtime = Runtime.getRuntime()
        val startMem = runtime.totalMemory() - runtime.freeMemory()

        log.info("Starting graph linking for app [id=${application.id}]...")

        // 1. Загрузка узлов
        val appId = requireNotNull(application.id) { "Application ID cannot be null" }

        val all = nodeRepo.findAllByApplicationId(appId, Pageable.ofSize(maxNodesToLoad))

        if (all.isEmpty()) {
            log.warn("No nodes found; skipping.")
            return
        }

        if (all.size >= maxNodesToLoad) {
            log.warn(
                "WARNING: Loaded maximum allowed nodes ($maxNodesToLoad). " +
                "There may be more nodes in the database. " +
                "Consider increasing docgen.graph.max-nodes configuration or implementing batch processing."
            )
        }

        log.info("Fetched ${all.size} total nodes to link.")

        // 2. Создание индекса
        val index = try {
            nodeIndexFactory.createMutable(all)
        } catch (e: Exception) {
            log.error("Failed to create node index for app [id=${application.id}]: ${e.message}", e)
            throw IllegalStateException("Cannot proceed with graph linking - index creation failed", e)
        }
        fun metaOf(n: Node): NodeMeta {
            return try {
                objectMapper.convertValue(n.meta, NodeMeta::class.java)
            } catch (e: IllegalArgumentException) {
                log.warn("Failed to convert node metadata: nodeId=${n.id}, fqn=${n.fqn}, error=${e.message}")
                NodeMeta() // Возвращаем пустой NodeMeta при ошибке конверсии
            } catch (e: Exception) {
                log.error("Unexpected error converting node metadata: nodeId=${n.id}, fqn=${n.fqn}", e)
                NodeMeta()
            }
        }

        // 3. Структурная линковка (последовательно, так как требует полного обзора)
        val structuralStart = System.currentTimeMillis()
        val structuralEdges = structuralEdgeLinker.linkContains(all, index, ::metaOf)
        val structuralDuration = System.currentTimeMillis() - structuralStart
        log.info(
                "Structural linking completed in ${structuralDuration}ms, created ${structuralEdges.size} edges"
        )

        // 4. Параллельная линковка узлов
        val parallelStart = System.currentTimeMillis()
        val progressStep = (all.size / 20).coerceAtLeast(1) // Логируем каждые 5%
        val processedCount = java.util.concurrent.atomic.AtomicInteger(0)

        // Используем контролируемый thread pool для предотвращения перегрузки системы
        val parallelism = Runtime.getRuntime().availableProcessors().coerceAtMost(8)
        val customPool = java.util.concurrent.ForkJoinPool(parallelism)

        val results = try {
            customPool.submit<List<NodeLinkResult>> {
                all.parallelStream()
                    .map { node ->
                        val currentCount = processedCount.incrementAndGet()
                        if (currentCount % progressStep == 0) {
                            val percent = (currentCount * 100.0 / all.size).toInt()
                            log.info("Linking progress: $percent% ($currentCount/${all.size})")
                        }

                        linkSingleNode(node, metaOf(node), index, application)
                    }
                    .toList()
            }.get(PARALLEL_LINKING_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        } catch (e: TimeoutException) {
            log.error("Parallel linking timed out after {} minutes for app [id={}]", PARALLEL_LINKING_TIMEOUT_MINUTES, application.id)
            throw IllegalStateException("Graph linking timed out after ${PARALLEL_LINKING_TIMEOUT_MINUTES} minutes", e)
        } finally {
            customPool.shutdown()
        }

        val parallelDuration = System.currentTimeMillis() - parallelStart
        log.info("Parallel linking completed in ${parallelDuration}ms")

        // 5. Агрегация результатов
        val edges = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val newlyCreatedNodes = mutableListOf<Node>()
        val libraryNodeEdges = mutableListOf<LibraryNodeEdgeProposal>()
        var callsErrors = 0

        edges += structuralEdges
        results.forEach { result ->
            edges += result.edges
            newlyCreatedNodes += result.newNodes
            libraryNodeEdges += result.libraryEdges
            if (result.error != null) callsErrors++
        }

        if (callsErrors > 0) {
            log.warn("Graph linking completed with {} errors out of {} nodes", callsErrors, all.size)
        }
        if (callsErrors >= MAX_ERROR_THRESHOLD) {
            log.error(
                "Too many linking errors ({}/{}). This may indicate a systemic problem.",
                callsErrors, all.size
            )
        }

        // 6. Обновление индекса новыми узлами
        val indexUpdateStart = System.currentTimeMillis()
        if (newlyCreatedNodes.isNotEmpty()) {
            log.info(
                    "Updating index with ${newlyCreatedNodes.size} newly created nodes (ENDPOINT/TOPIC)"
            )
            index.addNodes(newlyCreatedNodes)
        }
        val indexUpdateDuration = System.currentTimeMillis() - indexUpdateStart

        // 7. Сохранение в БД
        val persistenceStart = System.currentTimeMillis()
        sink.upsertEdges(
                edges.asSequence().map { (src, dst, kind) -> SimpleEdgeProposal(kind, src, dst) },
        )

        if (libraryNodeEdges.isNotEmpty()) {
            log.info("Saving ${libraryNodeEdges.size} direct links to library nodes")
            libraryNodeSink.upsertLibraryNodeEdges(libraryNodeEdges.asSequence())
        }
        val persistenceDuration = System.currentTimeMillis() - persistenceStart

        // 8. Сбор и логирование статистики
        val endTime = System.currentTimeMillis()
        val endMem = runtime.totalMemory() - runtime.freeMemory()
        val memoryUsedMb = (endMem - startMem) / 1024 / 1024

        val stats =
                LinkingStats(
                        totalNodes = all.size,
                        totalEdges = edges.size,
                        newIntegrationNodes = newlyCreatedNodes.size,
                        libraryNodeEdges = libraryNodeEdges.size,
                        callsErrors = callsErrors,
                        structuralLinkingDuration = java.time.Duration.ofMillis(structuralDuration),
                        parallelLinkingDuration = java.time.Duration.ofMillis(parallelDuration),
                        indexUpdateDuration = java.time.Duration.ofMillis(indexUpdateDuration),
                        persistenceDuration = java.time.Duration.ofMillis(persistenceDuration),
                        totalDuration = java.time.Duration.ofMillis(endTime - startTime),
                        memoryUsedMb = memoryUsedMb,
                        startedAt =
                                java.time.OffsetDateTime.now()
                                        .minusNanos((endTime - startTime) * 1_000_000),
                        finishedAt = java.time.OffsetDateTime.now(),
                )

        log.info(stats.toLogString())
    }

    /**
     * Линковка одного узла (thread-safe метод для параллельной обработки).
     * @param node Узел для линковки
     * @param meta Метаданные узла
     * @param index Индекс узлов
     * @param application Приложение
     * @return Результат линковки узла
     */
    private fun linkSingleNode(
            node: Node,
            meta: NodeMeta,
            index: NodeIndex,
            application: Application,
    ): NodeLinkResult {
        val nodeEdges = mutableListOf<Triple<Node, Node, EdgeKind>>()
        val nodeLibraryEdges = mutableListOf<LibraryNodeEdgeProposal>()
        val nodeNewNodes = mutableListOf<Node>()
        var error: Exception? = null

        try {
            // Линковка наследования (только для типов)
            if (node.isTypeNode()) {
                nodeEdges += inheritanceEdgeLinker.link(node, meta, index)
            }

            // Линковка аннотаций (для всех узлов)
            nodeEdges += annotationEdgeLinker.link(node, meta, index)

            // Линковка для функциональных узлов
            if (node.isFunctionNode()) {
                nodeEdges += signatureDependencyLinker.link(node, meta, index)

                try {
                    // Линковка вызовов
                    nodeEdges += callEdgeLinker.link(node, meta, index)

                    // Линковка интеграций (HTTP, Kafka, Camel)
                    val (integrationEdges, newNodes, libEdges) =
                            integrationEdgeLinker.linkIntegrationEdgesWithNodes(
                                    node,
                                    meta,
                                    index,
                                    application
                            )
                    nodeEdges += integrationEdges
                    nodeNewNodes += newNodes
                    nodeLibraryEdges += libEdges
                } catch (e: Exception) {
                    error = e
                    log.error("CALLS linking failed for ${node.fqn}: ${e.message}", e)
                }

                // Линковка исключений
                nodeEdges += throwEdgeLinker.link(node, meta, index)
            }
        } catch (e: Exception) {
            log.error("Failed to link node ${node.fqn}: ${e.message}", e)
        }

        return NodeLinkResult(nodeEdges, nodeNewNodes, nodeLibraryEdges, error)
    }

    /**
     * Результат линковки одного узла. Используется для агрегации результатов параллельной
     * обработки.
     */
    private data class NodeLinkResult(
            val edges: List<Triple<Node, Node, EdgeKind>>,
            val newNodes: List<Node>,
            val libraryEdges: List<LibraryNodeEdgeProposal>,
            val error: Exception?,
    )

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

    private fun Node.isFunctionNode(): Boolean =
            kind in setOf(NodeKind.METHOD, NodeKind.ENDPOINT, NodeKind.JOB, NodeKind.TOPIC)
}
