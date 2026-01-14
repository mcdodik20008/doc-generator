package com.bftcom.docgenerator.api.rag

import com.bftcom.docgenerator.api.rag.client.DocEvaluatorClient
import com.bftcom.docgenerator.api.rag.dto.RagRequest
import com.bftcom.docgenerator.api.rag.dto.ValidatedRagResponse
import com.bftcom.docgenerator.db.NodeRepository
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
        private val docEvaluatorClient: DocEvaluatorClient,
        private val nodeRepository: NodeRepository,
) {

    @PostMapping("/ask")
    fun ask(@RequestBody request: RagRequest): RagResponse {
        // todo: log request
        return ragService.ask(request.query, request.sessionId)
    }

    @PostMapping("/ask-with-val")
    fun askWithValidation(@RequestBody request: RagRequest): ValidatedRagResponse {
        // Получаем RAG ответ
        val ragResponse = ragService.ask(request.query, request.sessionId)

        // Пытаемся провалидировать ответ
        var validation: com.bftcom.docgenerator.api.rag.dto.EvaluationResult? = null
        var validationError: String? = null

        try {
            // Получаем первый source (если есть)
            val firstSource = ragResponse.sources.firstOrNull()

            if (firstSource != null) {
                // Пытаемся получить node по ID
                val nodeId = firstSource.id.toLongOrNull()

                if (nodeId != null) {
                    val node = nodeRepository.findById(nodeId).orElse(null)

                    if (node?.sourceCode != null && node.sourceCode!!.isNotBlank()) {
                        // Валидируем: sourceCode как code_snippet, answer как generated_doc
                        validation =
                                docEvaluatorClient.evaluate(
                                        codeSnippet = node.sourceCode!!,
                                        generatedDoc = ragResponse.answer
                                )

                        if (validation == null) {
                            validationError = "Doc-evaluator service unavailable or returned error"
                        }
                    } else {
                        validationError = "Node has no source code"
                    }
                } else {
                    validationError = "Invalid node ID in source"
                }
            } else {
                validationError = "No sources in RAG response"
            }
        } catch (e: Exception) {
            validationError = "Validation failed: ${e.message}"
        }

        return ValidatedRagResponse(
                validation = validation,
                ragResponse = ragResponse,
                validationError = validationError
        )
    }
}
