package com.bftcom.docgenerator.shared.util

/**
 * Утилиты нормализации URL/путей для сопоставления HTTP-эндпоинтов.
 *
 * Используется в IntegrationPointLinkerImpl, CrossAppGraphServiceImpl
 * и везде, где нужно привести URL к каноническому виду для сравнения.
 */
object UrlNormalizer {

    /**
     * Извлекает и нормализует путь из URL (убирает scheme, host, port).
     *
     * "https://ups-service:8080/ups/v1/findEstoDto" → "/ups/v1/findestodto"
     * "/api/users" → "/api/users"
     * Returns null if URL contains unresolved variables (e.g., ${...}).
     */
    fun normalizePath(url: String): String? {
        if (url.contains("\${")) return null

        val raw = try {
            val withoutScheme = if (url.contains("://")) {
                url.substringAfter("://")
            } else {
                url
            }
            val pathStart = withoutScheme.indexOf('/')
            if (pathStart < 0) "/" else withoutScheme.substring(pathStart)
        } catch (_: Exception) {
            return null
        }

        return canonicalize(raw)
    }

    /**
     * Проверяет совпадение двух путей с учётом нормализации.
     */
    fun pathsMatch(url1: String, url2: String): Boolean {
        val p1 = normalizePath(url1) ?: return false
        val p2 = normalizePath(url2) ?: return false
        return p1 == p2
    }

    /**
     * Нормализует HTTP-путь: ведущий /, без trailing /, lowercase.
     * Подходит для путей без scheme/host.
     */
    fun canonicalize(path: String): String {
        val trimmed = path.trim()
        val withLeadingSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return withLeadingSlash.trimEnd('/').lowercase().ifEmpty { "/" }
    }
}
