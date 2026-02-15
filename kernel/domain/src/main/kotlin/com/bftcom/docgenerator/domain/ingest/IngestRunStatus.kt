package com.bftcom.docgenerator.domain.ingest

enum class IngestRunStatus {
    PENDING, RUNNING, COMPLETED, FAILED, CANCELLED;

    companion object {
        fun fromString(value: String): IngestRunStatus =
            entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unknown IngestRunStatus: $value")
    }
}
