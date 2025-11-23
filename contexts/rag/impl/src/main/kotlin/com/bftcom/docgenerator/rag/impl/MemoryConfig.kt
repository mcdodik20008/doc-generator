package com.bftcom.docgenerator.rag.impl

import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class MemoryConfig {

    @Bean
    @Primary
    fun chatMemoryRepository(
        jdbcTemplate: JdbcTemplate,
        txManager: PlatformTransactionManager,
        customDialect: CustomPostgresDialect
    ): ChatMemoryRepository {
        return JdbcChatMemoryRepository.builder()
            .jdbcTemplate(jdbcTemplate)
            .transactionManager(txManager)
            .dialect(customDialect)
            .build()
    }
}