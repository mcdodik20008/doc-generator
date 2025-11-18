package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.graph.api.GraphBuilder
import com.bftcom.docgenerator.graph.api.declplanner.DeclPlanner
import com.bftcom.docgenerator.graph.api.linker.GraphLinker
import com.bftcom.docgenerator.graph.api.model.BuildResult
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.impl.apimetadata.ApiMetadataCollector
import com.bftcom.docgenerator.graph.impl.node.CommandExecutorImpl
import com.bftcom.docgenerator.graph.impl.node.KotlinSourceWalker
import com.bftcom.docgenerator.graph.impl.node.KotlinToDomainVisitor
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.nio.file.Path
import java.time.OffsetDateTime

@Service
class KotlinGraphBuilder(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
    private val kotlinWalker: KotlinSourceWalker,
    private val graphLinker: GraphLinker,
    private val transactionManager: PlatformTransactionManager,
    private val objectMapper: ObjectMapper,
    private val planners: List<DeclPlanner<*>>,
    private val nodeKindRefiner: NodeKindRefiner,
    private val apiMetadataCollector: ApiMetadataCollector? = null,
) : GraphBuilder {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun build(
        application: Application,
        sourceRoot: Path,
        classpath: List<File>,
    ): BuildResult {
        val started = OffsetDateTime.now()
        val tt = TransactionTemplate(transactionManager)

        val nodesBefore = nodeRepo.count()
        val edgesBefore = edgeRepo.count()
//        val chunksBefore = chunkRepo.count()

        // --- ФАЗА 1: создаём ноды ---
        val executor =
            tt.execute {
                val exec =
                    CommandExecutorImpl(
                        application = application,
                        nodeRepo = nodeRepo,
                        objectMapper = objectMapper,
                        nodeKindRefiner = nodeKindRefiner,
                        apiMetadataCollector = apiMetadataCollector,
                        libraryNodeEnricher = null, // Пока не используем при создании Node
                    )
                val visitor = KotlinToDomainVisitor(exec = exec, planners = planners)
                kotlinWalker.walk(sourceRoot, visitor, classpath)
                exec
            } ?: throw IllegalStateException("Failed to create nodes: transaction returned null")

        // Логируем статистику построения нод
        val stats = executor.getBuilderStats()
        log.info(
            "Node building stats: created={}, updated={}, skipped={}, total={}",
            stats.created,
            stats.updated,
            stats.skipped,
            stats.total,
        )

        // --- ФАЗА 2: линковка ---
        tt.execute { graphLinker.link(application) }

        val nodesAfter = nodeRepo.count()
        val edgesAfter = edgeRepo.count()
        val finished = OffsetDateTime.now()

        val result =
            BuildResult(
                nodes = (nodesAfter - nodesBefore).toInt(),
                edges = (edgesAfter - edgesBefore).toInt(),
                startedAt = started,
                finishedAt = finished,
            )
        log.info("Graph built: +${result.nodes} nodes, +${result.edges} edges")
        return result
    }
}
