package com.bftcom.docgenerator.graph.api.linker.model

/**
 * Результат валидации ребра перед записью в граф.
 * Показывает, прошло ли ребро проверку структурных и семантических инвариантов.
 */
data class ValidationResult(
    val isValid: Boolean,
    val message: String? = null,
    val warnings: List<String> = emptyList()
)
