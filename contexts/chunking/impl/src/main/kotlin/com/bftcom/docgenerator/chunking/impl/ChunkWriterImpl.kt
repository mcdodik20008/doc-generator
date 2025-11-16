package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.api.ChunkWriter
import com.bftcom.docgenerator.chunking.model.plan.ChunkPlan
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.chunk.Chunk
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

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

            val chunk =
                Chunk(
                    id = existing?.id,
                    application = plan.node.application,
                    node = plan.node,
                    source = plan.source,
                    kind = plan.kind,
                    langDetected = plan.lang,
                    contentRaw = null, // контент появится позже
                    content = "null",
                    contentTsv = null,
                    contentHash = null,
                    tokenCount = null,
                    chunkIndex = 0,
                    spanLines = plan.spanLines?.let { "[${it.first},${it.last}]" },
                    spanChars = null,
                    title = plan.title,
                    sectionPath = plan.sectionPath,
                    usesMd = null,
                    usedByMd = null,
                    emb = null,
                    embedModel = null,
                    embedTs = null,
                    explainMd = null,
                    explainQuality = emptyMap(),
                    relations = plan.relations.map { mapOf("kind" to it.kind, "dst_node_id" to it.dstNodeId) },
                    usedObjects = emptyList(),
                    pipeline =
                        mapOf(
                            "stages" to plan.pipeline.stages,
                            "params" to plan.pipeline.params,
                            "service" to plan.pipeline.service,
                        ),
                    freshnessAt = OffsetDateTime.now(),
                    rankBoost = 1.0f,
                    createdAt = OffsetDateTime.now(),
                    updatedAt = OffsetDateTime.now(),
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
