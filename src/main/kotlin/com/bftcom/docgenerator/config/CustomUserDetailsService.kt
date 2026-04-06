package com.bftcom.docgenerator.config

import com.bftcom.docgenerator.db.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.OffsetDateTime
import org.springframework.security.core.userdetails.User as SpringUser

/**
 * Сервис для загрузки пользователей из БД для Spring Security.
 *
 * Реализует ReactiveUserDetailsService для WebFlux.
 * Используется при form-based аутентификации.
 */
@Service
class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : ReactiveUserDetailsService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun findByUsername(username: String): Mono<UserDetails> =
        Mono
            .fromCallable {
                log.debug("Loading user by username: {}", username)
                val user =
                    userRepository.findByUsername(username)
                        ?: throw UsernameNotFoundException("User not found: $username")

                if (!user.enabled) {
                    log.warn("Disabled user attempted to login: {}", username)
                    throw UsernameNotFoundException("User is disabled: $username")
                }

                // Обновляем время последнего входа (асинхронно)
                Mono
                    .fromRunnable<Void> {
                        try {
                            user.lastLoginAt = OffsetDateTime.now()
                            userRepository.save(user)
                        } catch (e: Exception) {
                            log.debug("Failed to update last_login_at for user '{}': {}", username, e.message)
                        }
                    }.subscribeOn(Schedulers.boundedElastic())
                    .subscribe()

                // Конвертируем роли в authorities (добавляем префикс ROLE_)
                val authorities =
                    user.roles.map { role ->
                        if (role.startsWith("ROLE_")) {
                            SimpleGrantedAuthority(role)
                        } else {
                            SimpleGrantedAuthority("ROLE_$role")
                        }
                    }

                log.debug(
                    "User loaded: username={}, roles={}, authorities={}",
                    username,
                    user.roles.contentToString(),
                    authorities,
                )

                SpringUser
                    .builder()
                    .username(user.username)
                    .password(user.passwordHash)
                    .authorities(authorities)
                    .accountExpired(false)
                    .accountLocked(false)
                    .credentialsExpired(false)
                    .disabled(!user.enabled)
                    .build()
            }.subscribeOn(Schedulers.boundedElastic())
}
