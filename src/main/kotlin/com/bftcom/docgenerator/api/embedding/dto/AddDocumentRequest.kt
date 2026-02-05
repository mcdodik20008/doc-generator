package com.bftcom.docgenerator.api.embedding.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddDocumentRequest(
    @field:NotBlank
    @field:Size(max = 100, message = "Document ID cannot exceed 100 characters")
    val id: String,

    @field:NotBlank
    @field:Size(max = 50000, message = "Document content cannot exceed 50000 characters to prevent OOM")
    val content: String,

    val metadata: Map<String, Any> = emptyMap(),
)

