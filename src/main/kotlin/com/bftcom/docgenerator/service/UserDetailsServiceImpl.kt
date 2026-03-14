package com.bftcom.docgenerator.service

import com.bftcom.docgenerator.db.UserRepository
import com.bftcom.docgenerator.domain.user.User
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * Реализация ReactiveUserDetailsService для загрузки пользователей из БД.
 */
@Service
class UserDetailsServiceImpl(
    private val userRepository: UserRepository,
) : ReactiveUserDetailsService {

    override fun findByUsername(username: String): Mono<UserDetails> {
        return Mono.justOrEmpty(userRepository.findByUsername(username))
            .switchIfEmpty(Mono.error(UsernameNotFoundException("User not found: $username")))
            .map { user ->
                org.springframework.security.core.userdetails.User.builder()
                    .username(user.username)
                    .password(user.passwordHash)
                    .authorities(user.roles.map { SimpleGrantedAuthority("ROLE_$it") })
                    .disabled(!user.enabled)
                    .build()
            }
    }

    /**
     * Получает текущего аутентифицированного пользователя.
     */
    fun getCurrentUser(): Mono<User> {
        return ReactiveSecurityContextHolder.getContext()
            .map { it.authentication }
            .map { it.principal as UserDetails }
            .flatMap { userDetails ->
                Mono.justOrEmpty(userRepository.findByUsername(userDetails.username))
                    .switchIfEmpty(Mono.error(IllegalStateException("User not found: ${userDetails.username}")))
            }
    }

    /**
     * Получает ID текущего пользователя.
     */
    fun getCurrentUserId(): Mono<Long> {
        return getCurrentUser().map { it.id!! }
    }
}
