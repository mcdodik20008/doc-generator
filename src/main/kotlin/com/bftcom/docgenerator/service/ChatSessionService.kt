package com.bftcom.docgenerator.service

import com.bftcom.docgenerator.db.ChatSessionRepository
import com.bftcom.docgenerator.db.UserRepository
import com.bftcom.docgenerator.domain.chat.ChatSession
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Сервис для управления чат-сессиями пользователей.
 */
@Service
@Transactional
class ChatSessionService(
    private val chatSessionRepository: ChatSessionRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Получает все чаты пользователя, отсортированные по дате обновления (новые первыми).
     */
    fun getUserChats(userId: Long): List<ChatSessionDto> {
        val sessions = chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId)
        return sessions.map { toDto(it) }
    }

    /**
     * Получает последние N чатов пользователя.
     */
    fun getRecentChats(
        userId: Long,
        limit: Int = 10,
    ): List<ChatSessionDto> {
        val sessions = chatSessionRepository.findTop10ByUserIdOrderByUpdatedAtDesc(userId)
        return sessions.map { toDto(it) }
    }

    /**
     * Создает новый чат для пользователя.
     */
    fun createChat(
        userId: Long,
        title: String,
        applicationId: Long? = null,
    ): ChatSessionDto {
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalArgumentException("User not found: $userId")
            }

        val session =
            ChatSession(
                user = user,
                sessionId = UUID.randomUUID().toString(),
                title = title,
                messages = "[]",
                applicationId = applicationId,
            )

        val saved = chatSessionRepository.save(session)
        log.info("Created new chat session: id={}, userId={}, title={}", saved.id, userId, title)
        return toDto(saved)
    }

    /**
     * Получает чат по ID с проверкой прав доступа.
     */
    fun getChat(
        chatId: Long,
        userId: Long,
    ): ChatSessionDto {
        val session =
            chatSessionRepository.findByIdAndUserId(chatId, userId)
                ?: throw IllegalArgumentException("Chat not found or access denied: chatId=$chatId, userId=$userId")
        return toDto(session)
    }

    /**
     * Получает чат по session_id (UUID).
     */
    fun getChatBySessionId(sessionId: String): ChatSessionDto? {
        val session = chatSessionRepository.findBySessionId(sessionId) ?: return null
        return toDto(session)
    }

    /**
     * Добавляет сообщение в чат.
     */
    fun addMessage(
        sessionId: String,
        userId: Long,
        message: ChatMessageDto,
    ): ChatSessionDto {
        val session =
            chatSessionRepository.findBySessionId(sessionId)
                ?: throw IllegalArgumentException("Chat session not found: $sessionId")

        // Проверка прав доступа
        if (session.user.id != userId) {
            throw IllegalArgumentException("Access denied: sessionId=$sessionId, userId=$userId")
        }

        // Парсим существующие сообщения
        val messages =
            if (session.messages.isBlank() || session.messages == "[]") {
                mutableListOf<ChatMessageDto>()
            } else {
                objectMapper.readValue<MutableList<ChatMessageDto>>(session.messages)
            }

        // Добавляем новое сообщение
        messages.add(message)

        // Сохраняем обратно в JSON
        session.messages = objectMapper.writeValueAsString(messages)
        session.updatedAt = OffsetDateTime.now()

        val updated = chatSessionRepository.save(session)
        log.debug("Added message to chat: sessionId={}, role={}", sessionId, message.role)
        return toDto(updated)
    }

    /**
     * Обновляет название чата.
     */
    fun updateChatTitle(
        chatId: Long,
        userId: Long,
        newTitle: String,
    ): ChatSessionDto {
        val session =
            chatSessionRepository.findByIdAndUserId(chatId, userId)
                ?: throw IllegalArgumentException("Chat not found or access denied: chatId=$chatId, userId=$userId")

        session.title = newTitle
        session.updatedAt = OffsetDateTime.now()

        val updated = chatSessionRepository.save(session)
        log.info("Updated chat title: id={}, newTitle={}", chatId, newTitle)
        return toDto(updated)
    }

    /**
     * Удаляет чат пользователя.
     */
    fun deleteChat(
        chatId: Long,
        userId: Long,
    ): Boolean {
        val deleted = chatSessionRepository.deleteByIdAndUserId(chatId, userId)
        if (deleted > 0) {
            log.info("Deleted chat session: id={}, userId={}", chatId, userId)
            return true
        }
        return false
    }

    /**
     * Получает количество чатов пользователя.
     */
    fun getUserChatCount(userId: Long): Long = chatSessionRepository.countByUserId(userId)

    /**
     * Создает или получает существующий чат по session_id.
     * Используется при миграции с localStorage.
     */
    fun getOrCreateChat(
        sessionId: String,
        userId: Long,
        title: String = "New Chat",
    ): ChatSessionDto {
        // Проверяем существует ли чат с таким session_id
        val existing = chatSessionRepository.findBySessionId(sessionId)
        if (existing != null) {
            // Проверяем права доступа
            if (existing.user.id != userId) {
                log.warn(
                    "Session ID collision: sessionId={}, existingUserId={}, requestUserId={}",
                    sessionId,
                    existing.user.id,
                    userId,
                )
                // Создаем новый чат с новым UUID
                return createChat(userId, title)
            }
            return toDto(existing)
        }

        // Создаем новый чат с указанным session_id
        val user =
            userRepository.findById(userId).orElseThrow {
                IllegalArgumentException("User not found: $userId")
            }

        val session =
            ChatSession(
                user = user,
                sessionId = sessionId,
                title = title,
                messages = "[]",
            )

        val saved = chatSessionRepository.save(session)
        log.info("Created chat session with provided sessionId: id={}, userId={}, sessionId={}", saved.id, userId, sessionId)
        return toDto(saved)
    }

    private fun toDto(session: ChatSession): ChatSessionDto {
        val messages =
            if (session.messages.isBlank() || session.messages == "[]") {
                emptyList()
            } else {
                objectMapper.readValue<List<ChatMessageDto>>(session.messages)
            }

        return ChatSessionDto(
            id = session.id!!,
            sessionId = session.sessionId,
            title = session.title,
            messages = messages,
            applicationId = session.applicationId,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
        )
    }
}

/**
 * DTO для чат-сессии.
 */
data class ChatSessionDto(
    val id: Long,
    val sessionId: String,
    val title: String,
    val messages: List<ChatMessageDto>,
    val applicationId: Long?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

/**
 * DTO для сообщения в чате.
 * Совместимо с форматом localStorage на фронтенде.
 */
data class ChatMessageDto(
    val role: String, // "user" или "assistant"
    val data: Map<String, Any>, // { query: "...", answer: "...", sources: [...], metadata: {...} }
    val timestamp: Long,
)
