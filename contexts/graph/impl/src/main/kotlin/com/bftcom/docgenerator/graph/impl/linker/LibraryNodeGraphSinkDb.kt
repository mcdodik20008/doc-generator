package com.bftcom.docgenerator.graph.impl.linker

import com.bftcom.docgenerator.db.NodeLibraryNodeEdgeRepository
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeEdgeProposal
import com.bftcom.docgenerator.graph.api.linker.sink.LibraryNodeGraphSink
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LibraryNodeGraphSinkDb(
    private val nodeLibraryNodeEdgeRepository: NodeLibraryNodeEdgeRepository,
) : LibraryNodeGraphSink {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Сохраняет связи между Node и LibraryNode в БД.
     */
    @Transactional
    override fun upsertLibraryNodeEdges(edges: Sequence<LibraryNodeEdgeProposal>) {
        var written = 0
        var skipped = 0
        var errors = 0

        edges.forEach { e ->
            val nodeId = e.node.id
            val libraryNodeId = e.libraryNode.id
            if (nodeId != null && libraryNodeId != null) {
                try {
                    nodeLibraryNodeEdgeRepository.upsert(nodeId, libraryNodeId, e.kind.name)
                    written++
                } catch (ex: Exception) {
                    errors++
                    log.error(
                        "Failed to upsert library node edge: nodeId={}, libraryNodeId={}, kind={}, error={}",
                        nodeId,
                        libraryNodeId,
                        e.kind.name,
                        ex.message,
                        ex,
                    )
                }
            } else {
                skipped++
                log.trace(
                    "Skipping library node edge without id: nodeId={}, libraryNodeId={}",
                    nodeId,
                    libraryNodeId,
                )
            }
        }

        if (errors > 0) {
            log.warn("LibraryNodeGraphSinkDb: upserted={}, skipped={}, errors={}", written, skipped, errors)
        } else {
            log.info("LibraryNodeGraphSinkDb: upserted={}, skipped={}", written, skipped)
        }
    }
}

