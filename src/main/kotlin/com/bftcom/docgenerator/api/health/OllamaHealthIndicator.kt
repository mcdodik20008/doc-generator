package com.bftcom.docgenerator.api.health

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Health indicator that checks Ollama API availability.
 */
@Component("ollama")
class OllamaHealthIndicator(
    @Value("\${spring.ai.ollama.base-url:http://localhost:11434}") private val ollamaBaseUrl: String,
) : HealthIndicator {

    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    override fun health(): Health {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$ollamaBaseUrl/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val modelCount = try {
                    val body = response.body()
                    val regex = """"name"\s*:\s*"([^"]+)"""".toRegex()
                    regex.findAll(body).map { it.groupValues[1] }.toList()
                } catch (_: Exception) {
                    emptyList()
                }

                Health.up()
                    .withDetail("url", ollamaBaseUrl)
                    .withDetail("models", modelCount.joinToString(", ").ifEmpty { "unknown" })
                    .withDetail("modelCount", modelCount.size)
                    .build()
            } else {
                Health.down()
                    .withDetail("url", ollamaBaseUrl)
                    .withDetail("httpStatus", response.statusCode())
                    .build()
            }
        } catch (e: Exception) {
            log.debug("Ollama health check failed: {}", e.message)
            Health.down()
                .withDetail("url", ollamaBaseUrl)
                .withDetail("error", e.message ?: "Connection failed")
                .build()
        }
    }
}
