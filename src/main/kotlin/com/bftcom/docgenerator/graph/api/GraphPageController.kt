package com.bftcom.docgenerator.graph.api

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class GraphPageController {
    @GetMapping("/graph")
    fun page(): String = "graph"
}
