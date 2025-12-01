package com.bftcom.docgenerator.graph.api.node

/**
 * Вычислитель хешей исходного кода.
 * Используется для отслеживания изменений кода.
 */
interface CodeHasher {
    /**
     * Вычисляет хеш исходного кода.
     * @param sourceCode Исходный код
     * @return SHA-256 хеш в hex формате или null, если код пустой/null
     */
    fun computeHash(sourceCode: String?): String?
}

