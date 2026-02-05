package com.bftcom.docgenerator.api.common

/**
 * Аннотация для rate limiting endpoint'ов.
 * Ограничивает количество запросов на основе IP адреса.
 *
 * @param maxRequests Максимальное количество запросов
 * @param windowSeconds Временное окно в секундах
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimited(
    val maxRequests: Int = 100,
    val windowSeconds: Int = 60,
)
