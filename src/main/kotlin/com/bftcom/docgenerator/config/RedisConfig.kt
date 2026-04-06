package com.bftcom.docgenerator.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate

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

    @Bean
    fun stringRedisTemplate(
        connectionFactory: RedisConnectionFactory
    ): StringRedisTemplate {
        return StringRedisTemplate(connectionFactory)
    }
}
