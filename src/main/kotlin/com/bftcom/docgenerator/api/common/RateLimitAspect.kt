package com.bftcom.docgenerator.api.common

import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Аспект для rate limiting на основе IP адреса.
 * Использует простой in-memory счетчик с скользящим окном.
 */
@Aspect
@Component
class RateLimitAspect {
    private val log = LoggerFactory.getLogger(javaClass)

    // Хранилище: "IP:endpoint" -> RequestCounter
    private val requestCounts = ConcurrentHashMap<String, RequestCounter>()

    @Around("@annotation(rateLimited)")
    fun checkRateLimit(joinPoint: ProceedingJoinPoint, rateLimited: RateLimited): Any? {
        val request = getCurrentRequest()
        val clientIp = request?.remoteAddr ?: "unknown"
        val endpoint = (joinPoint.signature as MethodSignature).method.name

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

    private fun getCurrentRequest(): HttpServletRequest? {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        return attributes?.request
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
    }
}
