package com.bftcom.docgenerator.domain.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

/**
 * Пользователь системы для веб-аутентификации.
 *
 * Используется для защиты веб-интерфейса через form-based login.
 * Пароли хранятся в виде BCrypt хэшей.
 */
@Entity
@Table(name = "users", schema = "doc_generator")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
    @Column(nullable = false, unique = true, length = 100)
    var username: String,
    @Column(name = "password_hash", nullable = false, length = 60)
    var passwordHash: String,
    @Column(length = 255)
    var email: String? = null,
    @Column(nullable = false)
    var enabled: Boolean = true,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "roles", columnDefinition = "text[]")
    var roles: Array<String> = arrayOf("USER"),
    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "last_login_at")
    var lastLoginAt: OffsetDateTime? = null,
) {
    /**
     * Проверяет, имеет ли пользователь указанную роль.
     */
    fun hasRole(role: String): Boolean = roles.contains(role)

    /**
     * Проверяет, является ли пользователь администратором.
     */
    fun isAdmin(): Boolean = hasRole("ADMIN")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "User(id=$id, username='$username', enabled=$enabled, roles=${roles.contentToString()})"
}
