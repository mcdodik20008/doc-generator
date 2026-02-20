package com.bftcom.docgenerator.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * Redis configuration for rate limiting and caching.
 */
@Configuration
class RedisConfig {

    @Bean
    fun reactiveStringRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory
    ): ReactiveStringRedisTemplate {
        return ReactiveStringRedisTemplate(connectionFactory)
    }
}
