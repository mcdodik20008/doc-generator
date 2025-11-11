package com.bftcom.docgenerator.chunking.impl

import com.bftcom.docgenerator.chunking.api.ChunkWriter
import com.bftcom.docgenerator.chunking.model.plan.ChunkPlan
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.domain.chunk.Chunk
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class ChunkWriterImpl(
    private val chunkRepo: ChunkRepository,
) : ChunkWriter {
    override fun savePlan(plans: List<ChunkPlan>): ChunkWriter.SaveResult {
        var written = 0L

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

            chunkRepo.save(chunk)
            written++
        }

        return ChunkWriter.SaveResult(written = written, skipped = 0)
    }
}
