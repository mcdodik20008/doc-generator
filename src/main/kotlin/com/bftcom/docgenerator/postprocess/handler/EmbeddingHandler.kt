package com.bftcom.docgenerator.postprocess.handler

import com.bftcom.docgenerator.chunking.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.postprocess.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocess.model.FieldKey
import com.bftcom.docgenerator.postprocess.model.PartialMutation
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class EmbeddingHandler(
    private val client: EmbeddingClient?,
    @param:Value("\${docgen.embed.enabled:true}")
    private val enabled: Boolean,
) : PostprocessHandler {
    override fun supports(s: ChunkSnapshot) = enabled && client != null && !s.embPresent

    override fun produce(s: ChunkSnapshot): PartialMutation {
        val vec = client!!.embed(s.content)
        require(vec.size == client.dim)
        return PartialMutation()
            .set(FieldKey.EMB, vec)
            .set(FieldKey.EMBED_MODEL, client.modelName)
            .set(FieldKey.EMBED_TS, OffsetDateTime.now())
    }
}
