package com.bftcom.docgenerator.ai.resilience

/**
 * Interface for executing operations with resilience patterns (circuit breaker, retry, bulkhead).
 * Allows AI module to use resilience without direct dependency on main module.
 */
interface ResilientExecutor {

    /**
     * Execute an operation with resilience protection.
     *
     * @param operationName Human-readable name for logging/metrics
     * @param fallback Value to return when all retries fail or circuit is open
     * @param operation The actual operation to execute
     * @return Result of the operation or fallback value
     */
    fun <T> execute(operationName: String, fallback: T, operation: () -> T): T

    /**
     * Execute a string-returning operation with empty string fallback.
     */
    fun executeString(operationName: String, operation: () -> String): String {
        return execute(operationName, "", operation)
    }
}
