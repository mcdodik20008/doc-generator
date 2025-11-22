package com.bftcom.docgenerator.api.rag.dto

data class RagRequest(
        val query: String,
        val sessionId: String = "default",
)
