package com.bftcom.docgenerator.config

import com.bftcom.docgenerator.db.ApiKeyRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.security.MessageDigest
import java.time.OffsetDateTime

/**
 * WebFilter that authenticates requests to /api/ using X-API-Key header.
 *
 * Flow:
 * 1. Extract X-API-Key header
 * 2. Hash it with SHA-256
 * 3. Look up in DB
 * 4. Validate active + not expired
 * 5. Set SecurityContext with key name as principal and scopes as authorities
 *
 * If security is disabled via `docgen.security.enabled=false`, all requests pass through.
 */
@Component
class ApiKeyAuthenticationFilter(
    private val apiKeyRepository: ApiKeyRepository,
    @Value("\${docgen.security.enabled:true}") private val securityEnabled: Boolean,
) : WebFilter {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val API_KEY_HEADER = "X-API-Key"
    }

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val path = exchange.request.uri.path

        // Only intercept /api/ paths
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange)
        }

        // If security is disabled, pass through with anonymous auth
        if (!securityEnabled) {
            val anonymousAuth =
                UsernamePasswordAuthenticationToken(
                    "anonymous",
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
                )
            return chain
                .filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(anonymousAuth))
        }

        val apiKeyRaw = exchange.request.headers.getFirst(API_KEY_HEADER)

        if (apiKeyRaw.isNullOrBlank()) {
            log.debug("Missing API key for request: {} {}", exchange.request.method, path)
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            exchange.response.headers.set("WWW-Authenticate", "ApiKey")
            return exchange.response.setComplete()
        }

        val keyHash = hashApiKey(apiKeyRaw)

        return Mono
            .fromCallable {
                apiKeyRepository.findByKeyHash(keyHash)
            }.subscribeOn(Schedulers.boundedElastic())
            .flatMap { apiKey ->
                if (apiKey == null) {
                    log.warn("Invalid API key attempt for path: {}", path)
                    exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                    return@flatMap exchange.response.setComplete()
                }

                if (!apiKey.isValid()) {
                    log.warn("Expired/inactive API key '{}' used for path: {}", apiKey.name, path)
                    exchange.response.statusCode = HttpStatus.FORBIDDEN
                    return@flatMap exchange.response.setComplete()
                }

                // Update last_used_at asynchronously
                Mono
                    .fromRunnable<Void> {
                        try {
                            apiKey.lastUsedAt = OffsetDateTime.now()
                            apiKeyRepository.save(apiKey)
                        } catch (e: Exception) {
                            log.debug("Failed to update last_used_at for key '{}': {}", apiKey.name, e.message)
                        }
                    }.subscribeOn(Schedulers.boundedElastic())
                    .subscribe()

                // Build authorities from scopes
                val authorities =
                    apiKey.scopes.map { SimpleGrantedAuthority("SCOPE_$it") } +
                        SimpleGrantedAuthority("ROLE_API_USER")

                val authentication =
                    UsernamePasswordAuthenticationToken(
                        apiKey.name,
                        null,
                        authorities,
                    )

                chain
                    .filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            }.switchIfEmpty(
                Mono.defer {
                    exchange.response.statusCode = HttpStatus.UNAUTHORIZED
                    exchange.response.setComplete()
                },
            )
    }

    /**
     * Hashes an API key using SHA-256.
     */
    fun hashApiKey(rawKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(rawKey.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
