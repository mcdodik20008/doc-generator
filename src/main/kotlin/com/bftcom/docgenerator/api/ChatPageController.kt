package com.bftcom.docgenerator.api

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class ChatPageController {
    @GetMapping("/chat")
    fun page(): String = "chat"
}
