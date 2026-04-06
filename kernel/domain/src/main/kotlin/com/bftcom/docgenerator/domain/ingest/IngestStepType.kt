package com.bftcom.docgenerator.domain.ingest

enum class IngestStepType {
    CHECKOUT,
    RESOLVE_CLASSPATH,
    BUILD_LIBRARY,
    BUILD_GRAPH,
    LINK,
    ;

    companion object {
        fun fromString(value: String): IngestStepType =
            entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unknown IngestStepType: $value")
    }
}
