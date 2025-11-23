package com.bftcom.docgenerator.rag.impl

import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.vectorstore.pgvector.PgVectorStore
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate

@Configuration
class CustomPgVectorStoreConfig {

    @Bean
    @Primary
    fun customPgVectorStore(
        jdbcTemplate: JdbcTemplate,
        embeddingModel: EmbeddingModel
    ): PgVectorStore {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .schemaName("doc_generator")
            .vectorTableName("chunk")
            .distanceType(PgDistanceType.COSINE_DISTANCE)
            .initializeSchema(false)
            .dimensions(1024)
            .build()
    }
}