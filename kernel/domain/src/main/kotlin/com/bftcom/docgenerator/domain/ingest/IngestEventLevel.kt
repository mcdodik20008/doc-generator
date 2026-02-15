package com.bftcom.docgenerator.domain.ingest

enum class IngestEventLevel {
    INFO, WARN, ERROR;

    companion object {
        fun fromString(value: String): IngestEventLevel =
            entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unknown IngestEventLevel: $value")
    }
}
