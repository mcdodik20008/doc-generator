package com.bftcom.docgenerator.api

import com.bftcom.docgenerator.service.ChatSessionService
import com.bftcom.docgenerator.service.UserDetailsServiceImpl
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import reactor.core.publisher.Mono

/**
 * Контроллер страницы профиля пользователя.
 *
 * Отображает:
 * - Информацию о пользователе (username, email, roles)
 * - Список чатов пользователя
 * - Статистику использования
 */
@Controller
class ProfilePageController(
    private val userDetailsService: UserDetailsServiceImpl,
    private val chatSessionService: ChatSessionService,
) {
    @GetMapping("/profile")
    fun profilePage(model: Model): Mono<String> =
        userDetailsService
            .getCurrentUser()
            .flatMap { user ->
                val chats = chatSessionService.getUserChats(user.id!!)
                val chatCount = chatSessionService.getUserChatCount(user.id!!)

                model.addAttribute("user", user)
                model.addAttribute("chats", chats)
                model.addAttribute("chatCount", chatCount)

                Mono.just("profile")
            }
}
