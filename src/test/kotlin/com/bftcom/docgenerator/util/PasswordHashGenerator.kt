package com.bftcom.docgenerator.util

import org.junit.jupiter.api.Test
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * Утилита для генерации BCrypt хэшей паролей.
 * Запустите этот тест для получения хэша нужного пароля.
 */
class PasswordHashGenerator {
    @Test
    fun `generate BCrypt hash for admin password`() {
        val encoder = BCryptPasswordEncoder()
        val password = "admin123!@#"
        val hash = encoder.encode(password)

        println("========================================")
        println("Password: $password")
        println("BCrypt hash: $hash")
        println("========================================")

        // Verify hash works
        val matches = encoder.matches(password, hash)
        println("Verification: ${if (matches) "✓ SUCCESS" else "✗ FAILED"}")
        println("========================================")
    }
}
