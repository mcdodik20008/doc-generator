package com.bftcom.docgenerator.graph.api

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.graph.model.BuildResult
import java.nio.file.Path

interface GraphBuilder {
    fun build(
        application: Application,
        sourceRoot: Path,
    ): BuildResult
}
