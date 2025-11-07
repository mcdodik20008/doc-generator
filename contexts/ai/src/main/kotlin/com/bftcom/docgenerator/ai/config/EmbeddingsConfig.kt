package com.bftcom.docgenerator.ai.config

import com.bftcom.docgenerator.ai.embedding.EmbeddingClient
import com.bftcom.docgenerator.ai.embedding.ProxyEmbeddingClient
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EmbeddingsConfig(
    @Value("\${spring.ai.ollama.embedding.model}")
    private val modelName: String,
    @Value("\${spring.ai.ollama.embedding.dim}")
    private val dim: Int,
) {
    @Bean
    fun embeddingClient(embeddingModel: EmbeddingModel): EmbeddingClient = ProxyEmbeddingClient(embeddingModel, modelName, dim)
}
