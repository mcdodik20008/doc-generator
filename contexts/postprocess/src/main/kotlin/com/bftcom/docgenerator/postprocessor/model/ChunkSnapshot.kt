package com.bftcom.docgenerator.postprocessor.model

import com.bftcom.docgenerator.domain.chunk.Chunk
import java.time.OffsetDateTime

data class ChunkSnapshot(
    val id: Long,
    val content: String,
    val contentHash: String?,
    val tokenCount: Int?,
    val spanChars: String?, // int8range "[a,b)"
    val usesMd: String?,
    val usedByMd: String?,
    val explainMd: String?,
    val explainQualityJson: String?,
    val embPresent: Boolean,
    val embedModel: String?,
    val embedTs: OffsetDateTime?,
) {
    companion object {
        fun from(ch: Chunk) =
            ChunkSnapshot(
                id = ch.id!!,
                content = ch.content,
                contentHash = ch.contentHash,
                tokenCount = ch.tokenCount,
                spanChars = ch.spanChars,
                usesMd = ch.usesMd,
                usedByMd = ch.usedByMd,
                explainMd = ch.explainMd,
                explainQualityJson = if (ch.explainQuality.isEmpty()) null else ch.explainQuality.toString(),
                embPresent = (ch.emb != null),
                embedModel = ch.embedModel,
                embedTs = ch.embedTs,
            )
    }
}
