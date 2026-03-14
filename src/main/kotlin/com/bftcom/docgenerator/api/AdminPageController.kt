package com.bftcom.docgenerator.api

import com.bftcom.docgenerator.db.ChatSessionRepository
import com.bftcom.docgenerator.db.UserRepository
import com.bftcom.docgenerator.service.UserDetailsServiceImpl
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import reactor.core.publisher.Mono

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
        return userDetailsService.getCurrentUser()
            .map { currentUser ->
                // Проверяем что пользователь - админ
                if (!currentUser.isAdmin()) {
                    throw org.springframework.security.access.AccessDeniedException("Access denied")
                }

                // Статистика
                val totalUsers = userRepository.count()
                val enabledUsers = userRepository.findAllByEnabledTrue().size
                val totalChats = chatSessionRepository.count()

                model.addAttribute("currentUser", currentUser)
                model.addAttribute("totalUsers", totalUsers)
                model.addAttribute("enabledUsers", enabledUsers)
                model.addAttribute("totalChats", totalChats)

                "admin"
            }
    }
}
