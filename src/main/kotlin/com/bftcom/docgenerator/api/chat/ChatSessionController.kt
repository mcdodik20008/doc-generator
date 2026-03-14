package com.bftcom.docgenerator.api.chat

import com.bftcom.docgenerator.api.common.RateLimited
import com.bftcom.docgenerator.service.ChatSessionDto
import com.bftcom.docgenerator.service.ChatSessionService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST API для управления чат-сессиями пользователей.
 *
 * Endpoints:
 * - GET /api/chat/sessions - список чатов текущего пользователя
 * - POST /api/chat/sessions - создать новый чат
 * - GET /api/chat/sessions/{id} - получить чат с сообщениями
 * - PUT /api/chat/sessions/{id} - обновить название чата
 * - DELETE /api/chat/sessions/{id} - удалить чат
 */
@RestController
@RequestMapping("/api/chat/sessions")
class ChatSessionController(
    private val chatSessionService: ChatSessionService,
    private val userDetailsService: com.bftcom.docgenerator.service.UserDetailsServiceImpl,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Получает список всех чатов текущего пользователя.
     */
    @GetMapping
    @RateLimited(maxRequests = 100, windowSeconds = 60)
    fun getUserChats(
        authentication: Authentication,
        @RequestParam(required = false, defaultValue = "false") recent: Boolean,
    ): List<ChatSessionDto> {
        val userId = getUserId(authentication)
        return if (recent) {
            chatSessionService.getRecentChats(userId, limit = 10)
        } else {
            chatSessionService.getUserChats(userId)
        }
    }

    /**
     * Получает конкретный чат по ID.
     */
    @GetMapping("/{id}")
    @RateLimited(maxRequests = 100, windowSeconds = 60)
    fun getChat(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ChatSessionDto {
        val userId = getUserId(authentication)
        return chatSessionService.getChat(id, userId)
    }

    /**
     * Получает чат по session_id (UUID).
     * Используется для миграции с localStorage.
     */
    @GetMapping("/by-session-id/{sessionId}")
    @RateLimited(maxRequests = 100, windowSeconds = 60)
    fun getChatBySessionId(
        @PathVariable sessionId: String,
        authentication: Authentication,
    ): ChatSessionDto? {
        val userId = getUserId(authentication)
        val chat = chatSessionService.getChatBySessionId(sessionId)

        // Проверяем права доступа
        if (chat != null && chat.id != userId) {
            log.warn("Access denied: sessionId={}, userId={}", sessionId, userId)
            return null
        }

        return chat
    }

    /**
     * Создает новый чат.
     */
    @PostMapping
    @RateLimited(maxRequests = 50, windowSeconds = 60)
    fun createChat(
        @Valid @RequestBody request: CreateChatRequest,
        authentication: Authentication,
    ): ChatSessionDto {
        val userId = getUserId(authentication)
        return chatSessionService.createChat(
            userId = userId,
            title = request.title,
            applicationId = request.applicationId,
        )
    }

    /**
     * Создает чат с указанным session_id или получает существующий.
     * Используется для миграции с localStorage.
     */
    @PostMapping("/get-or-create")
    @RateLimited(maxRequests = 50, windowSeconds = 60)
    fun getOrCreateChat(
        @Valid @RequestBody request: GetOrCreateChatRequest,
        authentication: Authentication,
    ): ChatSessionDto {
        val userId = getUserId(authentication)
        return chatSessionService.getOrCreateChat(
            sessionId = request.sessionId,
            userId = userId,
            title = request.title ?: "New Chat",
        )
    }

    /**
     * Обновляет название чата.
     */
    @PutMapping("/{id}")
    @RateLimited(maxRequests = 50, windowSeconds = 60)
    fun updateChat(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateChatRequest,
        authentication: Authentication,
    ): ChatSessionDto {
        val userId = getUserId(authentication)
        return chatSessionService.updateChatTitle(id, userId, request.title)
    }

    /**
     * Удаляет чат.
     */
    @DeleteMapping("/{id}")
    @RateLimited(maxRequests = 30, windowSeconds = 60)
    fun deleteChat(
        @PathVariable id: Long,
        authentication: Authentication,
    ): Map<String, Any> {
        val userId = getUserId(authentication)
        val deleted = chatSessionService.deleteChat(id, userId)
        return mapOf(
            "success" to deleted,
            "message" to if (deleted) "Chat deleted successfully" else "Chat not found or access denied"
        )
    }

    /**
     * Получает статистику чатов пользователя.
     */
    @GetMapping("/stats")
    @RateLimited(maxRequests = 100, windowSeconds = 60)
    fun getChatStats(authentication: Authentication): Map<String, Any> {
        val userId = getUserId(authentication)
        val count = chatSessionService.getUserChatCount(userId)
        return mapOf(
            "totalChats" to count,
            "userId" to userId,
        )
    }

    /**
     * Извлекает ID пользователя из Security Context.
     */
    private fun getUserId(authentication: Authentication): Long {
        // Для Reactive WebFlux нужно использовать ReactiveSecurityContextHolder
        // Но так как это не suspend функция, используем block()
        return userDetailsService.getCurrentUserId().block()
            ?: throw IllegalStateException("User not authenticated")
    }
}

/**
 * Запрос на создание нового чата.
 */
data class CreateChatRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 255, message = "Title cannot exceed 255 characters")
    val title: String,

    val applicationId: Long? = null,
)

/**
 * Запрос на создание или получение чата по session_id.
 */
data class GetOrCreateChatRequest(
    @field:NotBlank(message = "Session ID cannot be blank")
    val sessionId: String,

    val title: String? = null,
)

/**
 * Запрос на обновление чата.
 */
data class UpdateChatRequest(
    @field:NotBlank(message = "Title cannot be blank")
    @field:Size(max = 255, message = "Title cannot exceed 255 characters")
    val title: String,
)
