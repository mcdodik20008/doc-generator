package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.api.ChunkWriter
import com.bftcom.docgenerator.chunking.model.plan.ChunkPlan
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.chunk.Chunk
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ChunkWriterImpl(
    private val chunkRepo: ChunkRepository,
) : ChunkWriter {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun savePlan(plans: List<ChunkPlan>): ChunkWriter.SaveResult {
        log.debug("Saving chunk plans: count={}", plans.size)
        var written = 0L
        var errors = 0L

        for (plan in plans) {
            val nodeId = plan.nodeId
            val existing = chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(nodeId)

            val content =
                (plan.node.sourceCode ?: plan.node.docComment ?: "")
                    .trim()
                    .ifBlank { "(empty)" }

            val chunk =
                Chunk(
                    id = existing?.id,
                    application = plan.node.application,
                    node = plan.node,
                    source = plan.source,
                    kind = plan.kind,
                    langDetected = plan.lang ?: "ru",
                    content = content,
                    contentHash = null,
                    tokenCount = null,
                    emb = null,
                    embedModel = null,
                    embedTs = null,
                )

            try {
                chunkRepo.save(chunk)
                written++
                log.trace("Saved chunk plan: nodeId={}, source={}, kind={}", nodeId, plan.source, plan.kind)
            } catch (e: Exception) {
                errors++
                log.error(
                    "Failed to save chunk plan: nodeId={}, source={}, kind={}, error={}",
                    nodeId,
                    plan.source,
                    plan.kind,
                    e.message,
                    e,
                )
            }
        }

        if (errors > 0) {
            log.warn("Chunk save completed with errors: written={}, errors={}", written, errors)
        } else {
            log.debug("Chunk save completed: written={}", written)
        }

        return ChunkWriter.SaveResult(written = written, skipped = 0)
    }
}
