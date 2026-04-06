package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    /**
     * Находит пользователя по имени (используется при логине).
     */
    fun findByUsername(username: String): User?

    /**
     * Находит всех активных пользователей.
     */
    fun findAllByEnabledTrue(): List<User>

    /**
     * Проверяет, существует ли пользователь с таким username.
     */
    fun existsByUsername(username: String): Boolean
}
