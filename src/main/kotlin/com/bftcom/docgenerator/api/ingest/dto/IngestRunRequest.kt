package com.bftcom.docgenerator.api.ingest.dto

import jakarta.validation.constraints.NotBlank

data class IngestRunRequest(
    @field:NotBlank val appKey: String, // ключ Application (например, "doc-generator")
    @field:NotBlank val repoGroup: String, // org/группа (например, "bftcom")
    @field:NotBlank val repoName: String, // репозиторий (например, "records-comparator")
    val branch: String? = null, // опционально (по умолчанию main/master — решает orchestrator)
    val depth: Int? = 1, // shallow clone по умолчанию
) {
    fun repoPath(): String = "$repoGroup/$repoName"
}