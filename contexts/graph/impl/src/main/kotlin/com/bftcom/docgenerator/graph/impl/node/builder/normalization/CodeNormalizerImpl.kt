package com.bftcom.docgenerator.graph.impl.node.builder.normalization

import com.bftcom.docgenerator.graph.api.node.CodeNormalizer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Реализация нормализатора исходного кода.
 */
@Component
class CodeNormalizerImpl : CodeNormalizer {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun normalize(sourceCode: String?, maxSize: Int): String? {
        if (sourceCode == null) return null

        return if (sourceCode.length > maxSize) {
            log.warn(
                "Source code truncated: originalSize={}, maxSize={}",
                sourceCode.length,
                maxSize,
            )
            sourceCode.take(maxSize) + "\n... [truncated]"
        } else {
            sourceCode
        }
    }

    override fun countLines(sourceCode: String): Int {
        if (sourceCode.isEmpty()) return 0
        // Нормализуем окончания строк и считаем
        return sourceCode.replace("\r\n", "\n").count { it == '\n' } + 1
    }
}

