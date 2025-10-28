package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.graph.api.GraphBuilder
import com.bftcom.docgenerator.graph.model.BuildResult
import com.bftcom.docgenerator.repo.ChunkRepository
import com.bftcom.docgenerator.repo.EdgeRepository
import com.bftcom.docgenerator.repo.NodeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Path

@Service
class KotlinGraphBuilder(
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
    private val chunkRepo: ChunkRepository,
    private val kotlinWalker: KotlinSourceWalker,
    private val graphLinker: GraphLinker,
    private val transactionManager: PlatformTransactionManager
) : GraphBuilder {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun build(application: Application, sourceRoot: Path): BuildResult {
        val tt = TransactionTemplate(transactionManager)

        val nodesBefore = nodeRepo.count()
        val edgesBefore = edgeRepo.count()
        val chunksBefore = chunkRepo.count()

        // Фаза 1 — запись/апсерт нод
        tt.execute {
            val visitor = KotlinToDomainVisitor(application, nodeRepo, edgeRepo)
            kotlinWalker.walk(sourceRoot, visitor)
        }

        // Фаза 2 — линковка
        tt.execute {
            graphLinker.link(application)
        }

        val nodesAfter = nodeRepo.count()
        val edgesAfter = edgeRepo.count()
        val chunksAfter = chunkRepo.count()
        val result = BuildResult(
            nodes = (nodesAfter - nodesBefore).toInt(),
            edges = (edgesAfter - edgesBefore).toInt(),
            chunks = (chunksAfter - chunksBefore).toInt(),
        )
        log.info("Graph built: +${result.nodes} nodes, +${result.edges} edges, +${result.chunks} chunks")
        return result
    }
}
