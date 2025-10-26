package com.bftcom.docgenerator.ai.model

import jakarta.validation.constraints.NotBlank

/** Ввод для coder: даём минимальный контекст кода/ноды */
data class CoderExplainRequest(
    @field:NotBlank val nodeFqn: String,
    @field:NotBlank val language: String,
    @field:NotBlank val codeExcerpt: String,   // ограничим выше по пайплайну
    val hints: String? = null                  // опционально (импорты, сигнатуры, вызовы)
)
