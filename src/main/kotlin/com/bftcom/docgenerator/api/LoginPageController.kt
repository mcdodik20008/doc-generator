package com.bftcom.docgenerator.api

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Контроллер страницы логина.
 *
 * Отображает форму аутентификации для доступа к веб-интерфейсу.
 */
@Controller
class LoginPageController {
    @GetMapping("/login")
    fun loginPage(): String = "login"
}
