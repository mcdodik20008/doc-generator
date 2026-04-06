package com.bftcom.docgenerator.service

import com.bftcom.docgenerator.db.UserRepository
import com.bftcom.docgenerator.domain.user.User
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * Сервис для получения текущего аутентифицированного пользователя.
 */
@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository,
) {
    /**
     * Получает текущего аутентифицированного пользователя.
     */
    fun getCurrentUser(): Mono<User> =
        ReactiveSecurityContextHolder
            .getContext()
            .map { it.authentication }
            .map { it.principal as UserDetails }
            .flatMap { userDetails ->
                Mono
                    .justOrEmpty(userRepository.findByUsername(userDetails.username))
                    .switchIfEmpty(Mono.error(IllegalStateException("User not found: ${userDetails.username}")))
            }

    /**
     * Получает ID текущего пользователя.
     */
    fun getCurrentUserId(): Mono<Long> = getCurrentUser().map { it.id!! }
}
