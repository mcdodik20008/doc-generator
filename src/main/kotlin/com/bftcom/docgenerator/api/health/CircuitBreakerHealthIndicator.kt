package com.bftcom.docgenerator.api.health

import com.bftcom.docgenerator.config.ResilientLlmWrapper
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

/**
 * Health indicator that reports the state of the LLM Circuit Breaker.
 *
 * States:
 * - CLOSED: Normal operation, all calls go through
 * - OPEN: Circuit is open due to failures, calls are rejected
 * - HALF_OPEN: Testing if service recovered by allowing limited calls
 */
@Component
class CircuitBreakerHealthIndicator(
    private val resilientLlmWrapper: ResilientLlmWrapper,
) : HealthIndicator {

    override fun health(): Health {
        val metrics = resilientLlmWrapper.getMetrics()
        val state = resilientLlmWrapper.getState()

        val builder = when (state.name) {
            "CLOSED" -> Health.up()
            "HALF_OPEN" -> Health.up()
            "OPEN" -> Health.down()
            else -> Health.unknown()
        }

        return builder
            .withDetails(metrics)
            .build()
    }
}
