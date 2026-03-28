package com.bftcom.docgenerator.api.admin

import com.bftcom.docgenerator.api.common.RateLimited
import com.bftcom.docgenerator.db.UserRepository
import com.bftcom.docgenerator.domain.user.User
import com.bftcom.docgenerator.service.ChatSessionService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST API для управления пользователями (только для администраторов).
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class UserManagementController(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val chatSessionService: ChatSessionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Получает список всех пользователей.
     */
    @GetMapping
    @RateLimited(maxRequests = 100, windowSeconds = 60)
    fun getAllUsers(): List<UserDto> = userRepository.findAll().map { toDto(it) }

    /**
     * Получает конкретного пользователя по ID.
     */
    @GetMapping("/{id}")
    @RateLimited(maxRequests = 100, windowSeconds = 60)
    fun getUser(
        @PathVariable id: Long,
    ): UserDto {
        val user =
            userRepository.findById(id).orElseThrow {
                IllegalArgumentException("User not found: $id")
            }
        return toDto(user)
    }

    /**
     * Создает нового пользователя.
     */
    @PostMapping
    @RateLimited(maxRequests = 20, windowSeconds = 60)
    fun createUser(
        @Valid @RequestBody request: CreateUserRequest,
    ): UserDto {
        // Проверяем что username уникален
        if (userRepository.existsByUsername(request.username)) {
            throw IllegalArgumentException("Username already exists: ${request.username}")
        }

        val user =
            User(
                username = request.username,
                passwordHash = passwordEncoder.encode(request.password),
                email = request.email,
                enabled = request.enabled ?: true,
                roles = request.roles?.toTypedArray() ?: arrayOf("USER"),
            )

        val saved = userRepository.save(user)
        log.info("Created new user: id={}, username={}, roles={}", saved.id, saved.username, saved.roles.contentToString())
        return toDto(saved)
    }

    /**
     * Обновляет пользователя.
     */
    @PutMapping("/{id}")
    @RateLimited(maxRequests = 50, windowSeconds = 60)
    fun updateUser(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateUserRequest,
    ): UserDto {
        val user =
            userRepository.findById(id).orElseThrow {
                IllegalArgumentException("User not found: $id")
            }

        request.email?.let { user.email = it }
        request.enabled?.let { user.enabled = it }
        request.roles?.let { user.roles = it.toTypedArray() }

        // Обновляем пароль только если указан
        request.password?.let {
            user.passwordHash = passwordEncoder.encode(it)
        }

        val updated = userRepository.save(user)
        log.info(
            "Updated user: id={}, username={}, enabled={}, roles={}",
            updated.id,
            updated.username,
            updated.enabled,
            updated.roles.contentToString(),
        )
        return toDto(updated)
    }

    /**
     * Удаляет пользователя.
     */
    @DeleteMapping("/{id}")
    @RateLimited(maxRequests = 20, windowSeconds = 60)
    fun deleteUser(
        @PathVariable id: Long,
    ): Map<String, Any> {
        val user =
            userRepository.findById(id).orElseThrow {
                IllegalArgumentException("User not found: $id")
            }

        userRepository.delete(user)
        log.warn("Deleted user: id={}, username={}", id, user.username)

        return mapOf(
            "success" to true,
            "message" to "User deleted successfully",
        )
    }

    /**
     * Получает статистику по пользователям.
     */
    @GetMapping("/stats")
    @RateLimited(maxRequests = 100, windowSeconds = 60)
    fun getUserStats(): Map<String, Any> {
        val totalUsers = userRepository.count()
        val enabledUsers = userRepository.findAllByEnabledTrue().size
        val adminUsers = userRepository.findAll().count { it.isAdmin() }

        return mapOf(
            "totalUsers" to totalUsers,
            "enabledUsers" to enabledUsers,
            "adminUsers" to adminUsers,
            "disabledUsers" to (totalUsers - enabledUsers),
        )
    }

    private fun toDto(user: User): UserDto {
        val chatCount = user.id?.let { chatSessionService.getUserChatCount(it) } ?: 0

        return UserDto(
            id = user.id!!,
            username = user.username,
            email = user.email,
            enabled = user.enabled,
            roles = user.roles.toList(),
            createdAt = user.createdAt,
            lastLoginAt = user.lastLoginAt,
            chatCount = chatCount,
        )
    }
}

data class UserDto(
    val id: Long,
    val username: String,
    val email: String?,
    val enabled: Boolean,
    val roles: List<String>,
    val createdAt: java.time.OffsetDateTime,
    val lastLoginAt: java.time.OffsetDateTime?,
    val chatCount: Long,
)

data class CreateUserRequest(
    @field:NotBlank(message = "Username cannot be blank")
    @field:Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    val username: String,
    @field:NotBlank(message = "Password cannot be blank")
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String,
    @field:Email(message = "Invalid email format")
    val email: String?,
    val enabled: Boolean? = true,
    val roles: List<String>? = null,
)

data class UpdateUserRequest(
    @field:Email(message = "Invalid email format")
    val email: String? = null,
    val enabled: Boolean? = null,
    val roles: List<String>? = null,
    @field:Size(min = 6, message = "Password must be at least 6 characters")
    val password: String? = null,
)
