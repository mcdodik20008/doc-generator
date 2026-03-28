package com.bftcom.docgenerator.config

import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * WebFilter that enforces rate limiting on API endpoints.
 *
 * Rate limiting strategy:
 * - Uses Redis to track request counts per API key across instances
 * - Different rate limits for different endpoint types
 * - Returns 429 Too Many Requests when limit exceeded
 *
 * Rate limit identifier: API key name (or "anonymous" for unauthenticated requests)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
class RateLimitFilter(
    private val redisTemplate: ReactiveStringRedisTemplate,
    @Value("\${docgen.rate-limit.enabled:true}") private val rateLimitEnabled: Boolean,
) : WebFilter {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        // Rate limits per minute
        private const val DEFAULT_LIMIT = 100
        private const val HIGH_LOAD_LIMIT = 30
        private const val MUTATION_LIMIT = 10

        // High-load endpoints (CPU/memory intensive)
        private val HIGH_LOAD_PATHS =
            setOf(
                "/api/rag/ask",
                "/api/embedding/search",
                "/api/embedding/documents",
            )

        // Mutation endpoints (write operations)
        private val MUTATION_PATHS =
            setOf(
                "/api/ingest/reindex",
                "/api/ingest/start",
                "/api/embedding/clear-postprocess",
            )
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        if (!rateLimitEnabled) {
            return chain.filter(exchange)
        }

        val path = exchange.request.uri.path

        // Only rate limit /api/ paths
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange)
        }

        return ReactiveSecurityContextHolder
            .getContext()
            .map { it.authentication?.name ?: "anonymous" }
            .defaultIfEmpty("anonymous")
            .flatMap { userId ->
                val limit = determineLimit(path)
                val key = "rate_limit:$userId:${path.substringBeforeLast("/")}"

                checkRateLimit(key, limit)
                    .flatMap { allowed ->
                        if (allowed) {
                            chain.filter(exchange)
                        } else {
                            log.warn("Rate limit exceeded for user '{}' on path '{}'", userId, path)
                            exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                            exchange.response.headers.set("X-RateLimit-Limit", limit.toString())
                            exchange.response.headers.set("Retry-After", "60")
                            exchange.response.setComplete()
                        }
                    }
            }
    }

    /**
     * Check if request is within rate limit using Redis.
     * Uses sliding window counter approach with sorted sets.
     */
    private fun checkRateLimit(
        key: String,
        limit: Int,
    ): Mono<Boolean> {
        val now = System.currentTimeMillis()
        val windowStart = now - Duration.ofMinutes(1).toMillis()

        val zSetOps = redisTemplate.opsForZSet()
        val windowRange = Range.closed(windowStart.toDouble(), now.toDouble())
        val oldRange = Range.closed(0.0, windowStart.toDouble())

        // Remove old entries and count current window
        return zSetOps
            .removeRangeByScore(key, oldRange)
            .then(zSetOps.count(key, windowRange))
            .flatMap { count ->
                if (count != null && count < limit) {
                    // Add current request timestamp
                    zSetOps
                        .add(key, now.toString(), now.toDouble())
                        .then(redisTemplate.expire(key, Duration.ofMinutes(2)))
                        .thenReturn(true)
                } else if (count == null || count >= limit) {
                    Mono.just(false)
                } else {
                    Mono.just(true)
                }
            }.onErrorResume { error ->
                // On Redis error, allow the request (fail open)
                log.error("Rate limit check failed for key '{}': {}", key, error.message)
                Mono.just(true)
            }
    }

    /**
     * Determine rate limit based on endpoint path.
     */
    private fun determineLimit(path: String): Int =
        when {
            MUTATION_PATHS.any { path.startsWith(it) } -> MUTATION_LIMIT
            HIGH_LOAD_PATHS.any { path.startsWith(it) } -> HIGH_LOAD_LIMIT
            else -> DEFAULT_LIMIT
        }
}
