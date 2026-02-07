package com.bftcom.docgenerator.api

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class DashboardPageController {
    @GetMapping("/")
    fun page(): String = "dashboard"
}
