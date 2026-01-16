package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.ai.chatclients.SummaryClient
import com.bftcom.docgenerator.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.FieldKey
import com.bftcom.docgenerator.postprocessor.model.PartialMutation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class EmbeddingHandler(
    private val client: EmbeddingClient?,
    private val summaryClient: SummaryClient,
    @param:Value("\${docgen.embed.enabled:true}")
    private val enabled: Boolean,
    @param:Value("\${docgen.embed.max-content-chars:30000}")
    private val maxContentChars: Int,
) : PostprocessHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(s: ChunkSnapshot) = enabled && client != null && !s.embeddingPresent

    override fun produce(s: ChunkSnapshot): PartialMutation {
        val content = s.content
        val contentForEmbedding =
            if (content.length > maxContentChars) {
                log.info(
                    "EmbeddingHandler: creating summary for chunkId={}, originalLength={}, maxLength={}",
                    s.id,
                    content.length,
                    maxContentChars,
                )
                val summary = summaryClient.summarize(content)
                if (summary.length > maxContentChars) {
                    log.warn(
                        "EmbeddingHandler: summary still too long ({}), truncating to {}",
                        summary.length,
                        maxContentChars,
                    )
                    summary.take(maxContentChars)
                } else {
                    summary
                }
            } else {
                content
            }

        val vec = client!!.embed(contentForEmbedding)
        require(vec.size == client.dim)
        return PartialMutation()
            .set(FieldKey.EMB, vec)
            .set(FieldKey.EMBED_MODEL, client.modelName)
            .set(FieldKey.EMBED_TS, OffsetDateTime.now())
    }
}
