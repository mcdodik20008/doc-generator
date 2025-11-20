package com.bftcom.docgenerator.api.embedding.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class SearchRequest(
    @field:NotBlank val query: String,
    @field:Positive val topK: Int = 10,
)

