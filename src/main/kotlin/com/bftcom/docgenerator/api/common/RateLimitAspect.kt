package com.bftcom.docgenerator.api.common

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.scheduling.annotation.Scheduled
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Аспект для rate limiting на основе IP адреса.
 * Использует простой in-memory счетчик с скользящим окном.
 * Совместим с WebFlux стеком — извлекает IP из аргументов метода или использует endpoint-based ключ.
 */
@Aspect
@Component
class RateLimitAspect {
    private val log = LoggerFactory.getLogger(javaClass)

    // Хранилище: "IP:endpoint" -> RequestCounter
    private val requestCounts = ConcurrentHashMap<String, RequestCounter>()

    @Around("@annotation(rateLimited)")
    fun checkRateLimit(joinPoint: ProceedingJoinPoint, rateLimited: RateLimited): Any? {
        val endpoint = (joinPoint.signature as MethodSignature).method.name

        // Пробуем извлечь IP из аргументов (ServerWebExchange или ServerHttpRequest)
        val clientIp = extractClientIp(joinPoint) ?: "unknown"

        val key = "$clientIp:$endpoint"
        val counter = requestCounts.computeIfAbsent(key) {
            RequestCounter(rateLimited.maxRequests, rateLimited.windowSeconds * 1000L)
        }

        if (!counter.allowRequest()) {
            log.warn("Rate limit exceeded for IP=$clientIp on endpoint=$endpoint")
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit exceeded. Max ${rateLimited.maxRequests} requests per ${rateLimited.windowSeconds} seconds."
            )
        }

        return joinPoint.proceed()
    }

    private fun extractClientIp(joinPoint: ProceedingJoinPoint): String? {
        for (arg in joinPoint.args) {
            when (arg) {
                is ServerWebExchange -> return arg.request.remoteAddress?.address?.hostAddress
                is ServerHttpRequest -> return arg.remoteAddress?.address?.hostAddress
            }
        }
        return null
    }

    private class RequestCounter(
        private val maxRequests: Int,
        private val windowMillis: Long,
    ) {
        private val count = AtomicInteger(0)
        private var windowStart = System.currentTimeMillis()

        @Synchronized
        fun allowRequest(): Boolean {
            val now = System.currentTimeMillis()

            // Если окно истекло, сбрасываем счетчик
            if (now - windowStart >= windowMillis) {
                windowStart = now
                count.set(0)
            }

            // Проверяем лимит
            val currentCount = count.incrementAndGet()
            return currentCount <= maxRequests
        }

        fun isExpired(now: Long): Boolean = now - windowStart >= windowMillis * 2
    }

    @Scheduled(fixedDelay = 300_000)
    fun cleanupStaleEntries() {
        val now = System.currentTimeMillis()
        val sizeBefore = requestCounts.size
        requestCounts.entries.removeIf { (_, counter) -> counter.isExpired(now) }
        val removed = sizeBefore - requestCounts.size
        if (removed > 0) {
            log.debug("RateLimitAspect cleanup: removed {} stale entries, remaining {}", removed, requestCounts.size)
        }
    }
}
