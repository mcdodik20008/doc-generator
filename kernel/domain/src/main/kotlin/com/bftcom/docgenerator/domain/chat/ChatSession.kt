package com.bftcom.docgenerator.domain.chat

import com.bftcom.docgenerator.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Чат-сессия пользователя.
 *
 * Хранит историю диалога пользователя с AI ассистентом.
 * Каждая сессия привязана к конкретному пользователю и содержит
 * список сообщений в формате JSON.
 */
@Entity
@Table(name = "chat_session", schema = "doc_generator")
class ChatSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /**
     * Пользователь-владелец чата
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    /**
     * UUID сессии (для совместимости с localStorage на фронтенде)
     */
    @Column(name = "session_id", nullable = false, unique = true, length = 36)
    var sessionId: String,

    /**
     * Название чата (автогенерируется или задается пользователем)
     */
    @Column(nullable = false, length = 255)
    var title: String,

    /**
     * История сообщений в формате JSON.
     * Структура: [{ role: 'user'|'assistant', data: {...}, timestamp: Long }]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "messages", columnDefinition = "jsonb", nullable = false)
    var messages: String = "[]",

    /**
     * ID приложения (если чат привязан к конкретному приложению)
     */
    @Column(name = "application_id")
    var applicationId: Long? = null,

    /**
     * Дата создания чата
     */
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    /**
     * Дата последнего обновления (последнего сообщения)
     */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatSession) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "ChatSession(id=$id, sessionId='$sessionId', title='$title', userId=${user.id})"
    }
}
