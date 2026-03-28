package com.bftcom.docgenerator.config

import com.bftcom.docgenerator.db.AuditLogRepository
import com.bftcom.docgenerator.domain.audit.AuditLog
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * WebFilter that logs all mutation requests (POST, PUT, DELETE, PATCH) to /api/ endpoints
 * into the audit_log table.
 *
 * Runs after authentication so the principal is available.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
class AuditWebFilter(
    private val auditLogRepository: AuditLogRepository,
) : WebFilter {
    private val log = LoggerFactory.getLogger(javaClass)

    private val mutationMethods =
        setOf(
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.DELETE,
            HttpMethod.PATCH,
        )

    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain,
    ): Mono<Void> {
        val request = exchange.request
        val path = request.uri.path

        // Only audit /api/ mutation requests
        if (!path.startsWith("/api/") || request.method !in mutationMethods) {
            return chain.filter(exchange)
        }

        val httpMethod = request.method.toString()
        val ipAddress = request.remoteAddress?.address?.hostAddress ?: "unknown"
        val action = deriveAction(httpMethod, path)

        return ReactiveSecurityContextHolder
            .getContext()
            .map { it.authentication?.name ?: "anonymous" }
            .defaultIfEmpty("anonymous")
            .flatMap { userId ->
                // Save audit log asynchronously — don't block the request
                Mono
                    .fromRunnable<Void> {
                        try {
                            val auditLog =
                                AuditLog(
                                    userId = userId,
                                    action = action,
                                    resource = path,
                                    httpMethod = httpMethod,
                                    ipAddress = ipAddress,
                                )
                            auditLogRepository.save(auditLog)
                            log.debug("Audit: user={}, action={}, resource={}, ip={}", userId, action, path, ipAddress)
                        } catch (e: Exception) {
                            log.warn("Failed to write audit log: {}", e.message)
                        }
                    }.subscribeOn(Schedulers.boundedElastic())
                    .subscribe()

                chain.filter(exchange)
            }
    }

    /**
     * Derives a human-readable action from HTTP method and path.
     */
    private fun deriveAction(
        method: String,
        path: String,
    ): String =
        when (method) {
            "POST" -> {
                when {
                    path.contains("/ingest/start") -> "INGEST_START"
                    path.contains("/ingest/reindex") -> "REINDEX"
                    path.contains("/api-keys") -> "API_KEY_CREATE"
                    path.contains("/rag/ask") -> "RAG_ASK"
                    path.contains("/embedding") -> "EMBEDDING_ADD"
                    path.contains("/chunks") -> "CHUNK_BUILD"
                    else -> "CREATE"
                }
            }

            "PUT" -> {
                "UPDATE"
            }

            "DELETE" -> {
                when {
                    path.contains("/api-keys") -> "API_KEY_REVOKE"
                    path.contains("/applications") -> "APPLICATION_DELETE"
                    else -> "DELETE"
                }
            }

            "PATCH" -> {
                "UPDATE"
            }

            else -> {
                "UNKNOWN"
            }
        }
}
