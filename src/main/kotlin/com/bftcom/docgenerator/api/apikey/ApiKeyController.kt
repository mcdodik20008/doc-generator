package com.bftcom.docgenerator.api.apikey

import com.bftcom.docgenerator.config.ApiKeyAuthenticationFilter
import com.bftcom.docgenerator.db.ApiKeyRepository
import com.bftcom.docgenerator.domain.apikey.ApiKey
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

data class CreateApiKeyRequest(
    val name: String,
    val scopes: List<String> = emptyList(),
    /** Expiry in days from now. Null = never expires */
    val expiresInDays: Int? = null,
)

data class ApiKeyResponse(
    val id: Long,
    val name: String,
    val scopes: List<String>,
    val active: Boolean,
    val createdAt: OffsetDateTime,
    val expiresAt: OffsetDateTime?,
    val lastUsedAt: OffsetDateTime?,
)

data class CreateApiKeyResponse(
    val id: Long,
    val name: String,
    /** Raw API key — shown only once at creation! */
    val rawKey: String,
    val scopes: List<String>,
    val expiresAt: OffsetDateTime?,
)

@RestController
@RequestMapping("/api/v1/api-keys")
class ApiKeyController(
    private val apiKeyRepository: ApiKeyRepository,
    private val authFilter: ApiKeyAuthenticationFilter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Create a new API key. Returns the raw key only once.
     */
    @PostMapping
    fun create(@RequestBody request: CreateApiKeyRequest): CreateApiKeyResponse {
        if (request.name.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "API key name is required")
        }

        // Generate a secure random key
        val rawKey = "dg_${UUID.randomUUID().toString().replace("-", "")}"
        val keyHash = authFilter.hashApiKey(rawKey)

        val expiresAt = request.expiresInDays?.let {
            OffsetDateTime.now().plusDays(it.toLong())
        }

        val apiKey = ApiKey(
            name = request.name,
            keyHash = keyHash,
            scopes = request.scopes.toTypedArray(),
            expiresAt = expiresAt,
        )

        val saved = apiKeyRepository.save(apiKey)
        log.info("API key created: name='{}', id={}, scopes={}", saved.name, saved.id, request.scopes)

        return CreateApiKeyResponse(
            id = saved.id!!,
            name = saved.name,
            rawKey = rawKey,
            scopes = request.scopes,
            expiresAt = expiresAt,
        )
    }

    /**
     * List all API keys (without revealing hash).
     */
    @GetMapping
    fun list(): List<ApiKeyResponse> {
        return apiKeyRepository.findAll().map { it.toResponse() }
    }

    /**
     * Revoke (soft delete) an API key.
     */
    @DeleteMapping("/{id}")
    fun revoke(@PathVariable id: Long) {
        val apiKey = apiKeyRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found: $id")
        }
        apiKey.active = false
        apiKeyRepository.save(apiKey)
        log.info("API key revoked: name='{}', id={}", apiKey.name, id)
    }

    private fun ApiKey.toResponse() = ApiKeyResponse(
        id = id!!,
        name = name,
        scopes = scopes.toList(),
        active = active,
        createdAt = createdAt,
        expiresAt = expiresAt,
        lastUsedAt = lastUsedAt,
    )
}
