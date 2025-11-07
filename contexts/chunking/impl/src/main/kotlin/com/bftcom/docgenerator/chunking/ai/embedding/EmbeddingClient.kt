package com.bftcom.docgenerator.chunking.ai.embedding

interface EmbeddingClient {
    val modelName: String
    val dim: Int

    fun embed(text: String): FloatArray
}
