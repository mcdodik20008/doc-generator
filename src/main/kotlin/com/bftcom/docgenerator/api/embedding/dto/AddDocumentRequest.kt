package com.bftcom.docgenerator.api.embedding.dto

import jakarta.validation.constraints.NotBlank

data class AddDocumentRequest(
    @field:NotBlank val id: String,
    @field:NotBlank val content: String,
    val metadata: Map<String, Any> = emptyMap(),
)

