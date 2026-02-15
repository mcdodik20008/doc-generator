package com.bftcom.docgenerator.domain.ingest

enum class IngestStepStatus {
    PENDING, RUNNING, COMPLETED, FAILED, SKIPPED;

    companion object {
        fun fromString(value: String): IngestStepStatus =
            entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unknown IngestStepStatus: $value")
    }
}
