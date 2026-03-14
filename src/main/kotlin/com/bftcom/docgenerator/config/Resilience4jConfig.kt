package com.bftcom.docgenerator.config

import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.github.resilience4j.retry.RetryRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Resilience4j configuration for LLM calls.
 *
 * Components:
 * - CircuitBreaker: stops calling LLM when failure rate exceeds threshold
 * - Retry: retries failed calls with exponential backoff
 * - Bulkhead: limits concurrent LLM calls to prevent resource exhaustion
 */
@Configuration
class Resilience4jConfig {

    companion object {
        const val LLM_CIRCUIT_BREAKER = "llmCircuitBreaker"
        const val LLM_RETRY = "llmRetry"
        const val LLM_BULKHEAD = "llmBulkhead"
    }

    @Bean
    fun llmCircuitBreakerRegistry(): CircuitBreakerRegistry {
        val config = CircuitBreakerConfig.custom()
            // Open circuit when 50% of calls fail
            .failureRateThreshold(50f)
            // Wait 60 seconds before attempting calls again
            .waitDurationInOpenState(Duration.ofSeconds(60))
            // Use sliding window of last 10 calls to calculate failure rate
            .slidingWindowSize(10)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            // Allow 5 calls in half-open state before deciding to close or re-open
            .permittedNumberOfCallsInHalfOpenState(5)
            // Локальная LLM (особенно reasoning-модель) может думать до часа.
            // Считаем медленным только если > 65 минут (с запасом к часовому HTTP-таймауту).
            .slowCallDurationThreshold(Duration.ofMinutes(65))
            .slowCallRateThreshold(80f)
            .build()

        return CircuitBreakerRegistry.of(config)
    }

    @Bean
    fun llmCircuitBreaker(registry: CircuitBreakerRegistry): CircuitBreaker {
        return registry.circuitBreaker(LLM_CIRCUIT_BREAKER)
    }

    @Bean
    fun llmRetryRegistry(): RetryRegistry {
        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
//            .waitDuration(Duration.ofSeconds(2))
            // Exponential backoff: 2s, 4s, 8s
            .intervalFunction { attempt -> (attempt * 2000).toLong() }
            // Ретраим только сетевые ошибки (разрыв соединения и пр.),
            // но НЕ таймауты — при таймауте LLM повторный вызов бесполезен
            .retryExceptions(
                java.net.ConnectException::class.java,
                java.net.UnknownHostException::class.java,
                org.springframework.web.reactive.function.client.WebClientResponseException::class.java,
            )
            .ignoreExceptions(
                IllegalArgumentException::class.java,
            )
            .build()

        return RetryRegistry.of(config)
    }

    @Bean
    fun llmRetry(registry: RetryRegistry): Retry {
        return registry.retry(LLM_RETRY)
    }

    @Bean
    fun llmBulkheadRegistry(): BulkheadRegistry {
        val config = BulkheadConfig.custom()
            // Maximum 5 concurrent LLM calls
            .maxConcurrentCalls(5)
            // Wait up to 10 seconds for a permit
            .maxWaitDuration(Duration.ofSeconds(10))
            .build()

        return BulkheadRegistry.of(config)
    }

    @Bean
    fun llmBulkhead(registry: BulkheadRegistry): Bulkhead {
        return registry.bulkhead(LLM_BULKHEAD)
    }
}
