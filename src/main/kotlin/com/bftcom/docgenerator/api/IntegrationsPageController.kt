package com.bftcom.docgenerator.api

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class IntegrationsPageController {
    @GetMapping("/integrations")
    fun page(): String = "integrations"
}
