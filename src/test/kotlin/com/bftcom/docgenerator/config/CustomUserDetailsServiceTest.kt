package com.bftcom.docgenerator.config

import com.bftcom.docgenerator.db.UserRepository
import com.bftcom.docgenerator.domain.user.User
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.core.userdetails.UsernameNotFoundException
import reactor.test.StepVerifier

class CustomUserDetailsServiceTest {
    private val userRepository: UserRepository = mock()
    private val userDetailsService = CustomUserDetailsService(userRepository)

    @Test
    fun `findByUsername should return UserDetails for existing active user`() {
        // Given
        val user =
            User(
                id = 1L,
                username = "testuser",
                passwordHash = "\$2a\$10\$hashedhashed",
                email = "test@test.com",
                enabled = true,
                roles = arrayOf("ADMIN", "USER"),
            )

        `when`(userRepository.findByUsername("testuser")).thenReturn(user)

        // When
        val result = userDetailsService.findByUsername("testuser")

        // Then
        StepVerifier
            .create(result)
            .assertNext { userDetails ->
                assertEquals("testuser", userDetails.username)
                assertEquals("\$2a\$10\$hashedhashed", userDetails.password)
                assertTrue(userDetails.isEnabled)
                assertTrue(userDetails.authorities.any { it.authority == "ROLE_ADMIN" })
                assertTrue(userDetails.authorities.any { it.authority == "ROLE_USER" })
            }.verifyComplete()
    }

    @Test
    fun `findByUsername should throw exception for non-existing user`() {
        // Given
        `when`(userRepository.findByUsername("nonexistent")).thenReturn(null)

        // When
        val result = userDetailsService.findByUsername("nonexistent")

        // Then
        StepVerifier
            .create(result)
            .expectError(UsernameNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `findByUsername should throw exception for disabled user`() {
        // Given
        val disabledUser =
            User(
                id = 2L,
                username = "disabled",
                passwordHash = "\$2a\$10\$hashedhashed",
                email = "disabled@test.com",
                enabled = false,
                roles = arrayOf("USER"),
            )

        `when`(userRepository.findByUsername("disabled")).thenReturn(disabledUser)

        // When
        val result = userDetailsService.findByUsername("disabled")

        // Then
        StepVerifier
            .create(result)
            .expectError(UsernameNotFoundException::class.java)
            .verify()
    }

    @Test
    fun `findByUsername should handle roles without ROLE_ prefix`() {
        // Given
        val user =
            User(
                id = 3L,
                username = "normaluser",
                passwordHash = "\$2a\$10\$hashedhashed",
                enabled = true,
                roles = arrayOf("ADMIN", "ROLE_EXISTING"), // Mix of with and without prefix
            )

        `when`(userRepository.findByUsername("normaluser")).thenReturn(user)

        // When
        val result = userDetailsService.findByUsername("normaluser")

        // Then
        StepVerifier
            .create(result)
            .assertNext { userDetails ->
                assertEquals("normaluser", userDetails.username)
                // Should have ROLE_ prefix added to "ADMIN"
                assertTrue(userDetails.authorities.any { it.authority == "ROLE_ADMIN" })
                // Should keep existing ROLE_ prefix
                assertTrue(userDetails.authorities.any { it.authority == "ROLE_EXISTING" })
                assertEquals(2, userDetails.authorities.size)
            }.verifyComplete()
    }
}
