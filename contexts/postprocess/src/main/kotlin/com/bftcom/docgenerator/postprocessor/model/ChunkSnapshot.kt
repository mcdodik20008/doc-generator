package com.bftcom.docgenerator.postprocessor.model

import com.bftcom.docgenerator.domain.chunk.Chunk
import java.time.OffsetDateTime

data class ChunkSnapshot(
    val id: Long,
    val content: String,
    val contentHash: String?,
    val tokenCount: Int?,
    val embeddingPresent: Boolean,
    val embedModel: String?,
    val embedTs: OffsetDateTime?,
) {
    companion object {
        fun from(ch: Chunk) =
            ChunkSnapshot(
                id = requireNotNull(ch.id) { "Chunk must be persisted" },
                content = ch.content,
                contentHash = ch.contentHash,
                tokenCount = ch.tokenCount,
                embeddingPresent = (ch.embedTs != null),
                embedModel = ch.embedModel,
                embedTs = ch.embedTs,
            )
    }
}
