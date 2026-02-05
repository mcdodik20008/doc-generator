package com.bftcom.docgenerator.api.embedding.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class SearchRequest(
    @field:NotBlank val query: String,
    @field:Positive
    @field:Max(value = 1000, message = "topK cannot exceed 1000 to prevent OOM")
    val topK: Int = 10,
)

