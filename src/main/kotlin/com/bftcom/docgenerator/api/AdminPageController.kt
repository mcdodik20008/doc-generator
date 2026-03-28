package com.bftcom.docgenerator.api

import com.bftcom.docgenerator.db.ChatSessionRepository
import com.bftcom.docgenerator.db.UserRepository
import com.bftcom.docgenerator.domain.user.User
import com.bftcom.docgenerator.service.UserDetailsServiceImpl
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Контроллер админ-панели (доступен только администраторам).
 *
 * Отображает:
 * - Статистику системы
 * - Управление пользователями
 * - Системные настройки
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
class AdminPageController(
    private val userRepository: UserRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val userDetailsService: UserDetailsServiceImpl,
) {
    @GetMapping("/admin")
    fun adminPage(model: Model): Mono<String> {
        return userDetailsService
            .getCurrentUser()
            .publishOn(Schedulers.boundedElastic())
            .handle { currentUser: User, sink ->
                // Проверяем что пользователь - админ
                if (!currentUser.isAdmin()) {
                    sink.error(AccessDeniedException("Access denied"))
                    return@handle
                }

                // Статистика
                val totalUsers = userRepository.count()
                val enabledUsers = userRepository.findAllByEnabledTrue().size
                val totalChats = chatSessionRepository.count()

                model.addAttribute("currentUser", currentUser)
                model.addAttribute("totalUsers", totalUsers)
                model.addAttribute("enabledUsers", enabledUsers)
                model.addAttribute("totalChats", totalChats)

                sink.next("admin")
            }
    }
}
