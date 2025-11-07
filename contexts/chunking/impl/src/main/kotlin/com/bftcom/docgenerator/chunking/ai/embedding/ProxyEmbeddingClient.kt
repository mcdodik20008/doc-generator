package com.bftcom.docgenerator.chunking.ai.embedding

import org.springframework.ai.embedding.EmbeddingModel

class ProxyEmbeddingClient(
    private val model: EmbeddingModel,
    override val modelName: String,
    override val dim: Int,
) : EmbeddingClient {
    override fun embed(text: String): FloatArray {
        val vec = model.embed(text)
        require(vec.size == dim) {
            "Embedding dim ${vec.size} != expected $dim (model=$modelName)"
        }
        return vec
    }
}
