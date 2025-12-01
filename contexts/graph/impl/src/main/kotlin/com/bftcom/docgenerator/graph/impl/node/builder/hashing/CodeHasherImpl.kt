package com.bftcom.docgenerator.graph.impl.node.builder.hashing

import com.bftcom.docgenerator.graph.api.node.CodeHasher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * Реализация вычислителя хешей исходного кода.
 * Использует SHA-256 для вычисления хеша.
 */
@Component
class CodeHasherImpl : CodeHasher {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun computeHash(sourceCode: String?): String? {
        if (sourceCode.isNullOrBlank()) return null

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(sourceCode.toByteArray(Charsets.UTF_8))
            val hash = hashBytes.joinToString("") { "%02x".format(it) }
            log.trace("Computed code hash: length={}, hash={}", sourceCode.length, hash.take(16))
            hash
        } catch (e: Exception) {
            log.warn("Failed to compute code hash: {}", e.message, e)
            null
        }
    }
}

