package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.chat.ChatSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ChatSessionRepository : JpaRepository<ChatSession, Long> {

    /**
     * Находит все чаты пользователя, отсортированные по дате обновления (новые первыми).
     */
    fun findByUserIdOrderByUpdatedAtDesc(userId: Long): List<ChatSession>

    /**
     * Находит чат по session_id (UUID).
     */
    fun findBySessionId(sessionId: String): ChatSession?

    /**
     * Находит чат по ID и владельцу (для проверки прав доступа).
     */
    fun findByIdAndUserId(id: Long, userId: Long): ChatSession?

    /**
     * Удаляет чат только если он принадлежит указанному пользователю.
     */
    @Modifying
    @Query("DELETE FROM ChatSession c WHERE c.id = :id AND c.user.id = :userId")
    fun deleteByIdAndUserId(@Param("id") id: Long, @Param("userId") userId: Long): Int

    /**
     * Проверяет, существует ли чат с указанным session_id.
     */
    fun existsBySessionId(sessionId: String): Boolean

    /**
     * Подсчитывает количество чатов пользователя.
     */
    fun countByUserId(userId: Long): Long

    /**
     * Находит последние N чатов пользователя.
     */
    fun findTop10ByUserIdOrderByUpdatedAtDesc(userId: Long): List<ChatSession>
}
