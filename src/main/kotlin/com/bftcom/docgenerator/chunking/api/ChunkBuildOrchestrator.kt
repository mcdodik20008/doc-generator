package com.bftcom.docgenerator.chunking.api

import com.bftcom.docgenerator.api.dto.ChunkBuildRequest
import com.bftcom.docgenerator.api.dto.ChunkBuildStatusDto
import com.bftcom.docgenerator.chunking.model.ChunkRunHandle

interface ChunkBuildOrchestrator {
    fun start(req: ChunkBuildRequest): ChunkRunHandle

    fun status(runId: String): ChunkBuildStatusDto
}
