package com.bftcom.docgenerator.ai.model

import jakarta.validation.constraints.NotBlank

data class TalkerRewriteRequest(
    @field:NotBlank val nodeFqn: String,
    @field:NotBlank val language: String,
    @field:NotBlank val rawContent: String,
)
