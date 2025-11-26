package com.bftcom.docgenerator.api.rag

import com.bftcom.docgenerator.api.rag.dto.RagRequest
import com.bftcom.docgenerator.rag.api.RagResponse
import com.bftcom.docgenerator.rag.api.RagService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/rag")
class RagController(
        private val ragService: RagService,
) {

    @PostMapping("/ask")
    fun ask(@RequestBody request: RagRequest): RagResponse {
        // todo: log request
        return ragService.ask(request.query, request.sessionId)
    }
}
