package com.bftcom.docgenerator.api.rag.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RagRequest(
    @field:NotBlank(message = "Query cannot be blank")
    @field:Size(min = 3, max = 5000, message = "Query must be between 3 and 5000 characters")
    val query: String,

    @field:NotBlank(message = "Session ID cannot be blank")
    @field:Size(max = 100, message = "Session ID cannot exceed 100 characters")
    val sessionId: String = "default",
)
