package com.bftcom.docgenerator.api.ingest.dto

data class IngestStartRequest(
    val branch: String? = null,
    val triggeredBy: String? = null,
)
