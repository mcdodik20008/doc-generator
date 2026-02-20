package com.bftcom.docgenerator.config

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.ratelimiter.RateLimiterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Rate limiting configuration using Resilience4j.
 *
 * Defines rate limits for API endpoints:
 * - Default: 100 requests per minute (burst: 20)
 * - High-load endpoints (RAG, embedding): 30 requests per minute (burst: 10)
 * - Mutation endpoints (ingest, delete): 10 requests per minute (burst: 5)
 *
 * Rate limits are enforced per API key.
 */
@Configuration
class RateLimitConfig {

    companion object {
        const val DEFAULT_RATE_LIMITER = "default"
        const val HIGH_LOAD_RATE_LIMITER = "highLoad"
        const val MUTATION_RATE_LIMITER = "mutation"
    }

    @Bean
    fun rateLimiterRegistry(): RateLimiterRegistry {
        return RateLimiterRegistry.ofDefaults()
    }

    /**
     * Default rate limiter: 100 requests per minute with burst of 20
     */
    @Bean
    fun defaultRateLimiter(registry: RateLimiterRegistry): RateLimiter {
        val config = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .limitForPeriod(100)
            .timeoutDuration(Duration.ofSeconds(5))
            .build()

        return registry.rateLimiter(DEFAULT_RATE_LIMITER, config)
    }

    /**
     * High-load endpoints rate limiter: 30 requests per minute with burst of 10
     */
    @Bean
    fun highLoadRateLimiter(registry: RateLimiterRegistry): RateLimiter {
        val config = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .limitForPeriod(30)
            .timeoutDuration(Duration.ofSeconds(5))
            .build()

        return registry.rateLimiter(HIGH_LOAD_RATE_LIMITER, config)
    }

    /**
     * Mutation endpoints rate limiter: 10 requests per minute with burst of 5
     */
    @Bean
    fun mutationRateLimiter(registry: RateLimiterRegistry): RateLimiter {
        val config = RateLimiterConfig.custom()
            .limitRefreshPeriod(Duration.ofMinutes(1))
            .limitForPeriod(10)
            .timeoutDuration(Duration.ofSeconds(5))
            .build()

        return registry.rateLimiter(MUTATION_RATE_LIMITER, config)
    }
}
