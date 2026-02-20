package com.bftcom.docgenerator.config

import com.bftcom.docgenerator.ai.resilience.ResilientExecutor
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.function.Supplier

/**
 * Wraps LLM calls with Resilience4j Circuit Breaker, Retry, and Bulkhead.
 *
 * Usage:
 * ```kotlin
 * val result = resilientLlmWrapper.execute("operationName") {
 *     chatClient.prompt().user(query).call().content()
 * }
 * ```
 *
 * If the circuit is open or all retries fail, returns the fallback value (empty string by default).
 */
@Service
class ResilientLlmWrapper(
    private val circuitBreaker: CircuitBreaker,
    private val retry: Retry,
    private val bulkhead: Bulkhead,
) : ResilientExecutor {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Execute an LLM call with circuit breaker, retry, and bulkhead protection.
     *
     * @param operationName Human-readable name for logging
     * @param fallback Value to return when all retries fail or circuit is open
     * @param operation The actual LLM call
     * @return Result of the LLM call or fallback value
     */
    override fun <T> execute(
        operationName: String,
        fallback: T,
        operation: () -> T,
    ): T {
        val decoratedSupplier = Bulkhead.decorateSupplier(bulkhead,
            CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, Supplier { operation() })
            )
        )

        return try {
            decoratedSupplier.get()
        } catch (e: CallNotPermittedException) {
            log.warn(
                "Circuit breaker OPEN for operation '{}'. State: {}. Returning fallback.",
                operationName, circuitBreaker.state
            )
            fallback
        } catch (e: io.github.resilience4j.bulkhead.BulkheadFullException) {
            log.warn(
                "Bulkhead full for operation '{}'. Max concurrent calls reached. Returning fallback.",
                operationName
            )
            fallback
        } catch (e: Exception) {
            log.error(
                "LLM call '{}' failed after retries. Circuit state: {}. Error: {}",
                operationName, circuitBreaker.state, e.message
            )
            fallback
        }
    }

    /**
     * Execute a string-returning LLM call with empty string fallback.
     */
    override fun executeString(operationName: String, operation: () -> String): String {
        return execute(operationName, "", operation)
    }

    /**
     * Get current circuit breaker state.
     */
    fun getState(): CircuitBreaker.State = circuitBreaker.state

    /**
     * Get circuit breaker metrics.
     */
    fun getMetrics(): Map<String, Any> {
        val metrics = circuitBreaker.metrics
        return mapOf(
            "state" to circuitBreaker.state.name,
            "failureRate" to metrics.failureRate,
            "slowCallRate" to metrics.slowCallRate,
            "numberOfBufferedCalls" to metrics.numberOfBufferedCalls,
            "numberOfFailedCalls" to metrics.numberOfFailedCalls,
            "numberOfSuccessfulCalls" to metrics.numberOfSuccessfulCalls,
            "numberOfSlowCalls" to metrics.numberOfSlowCalls,
            "numberOfNotPermittedCalls" to metrics.numberOfNotPermittedCalls,
        )
    }
}
