package com.bftcom.docgenerator.api.graph

import com.bftcom.docgenerator.model.BuildResult
import java.io.File
import java.nio.file.Path

interface GraphBuilder {
    fun build(
        application: com.bftcom.docgenerator.domain.application.Application,
        sourceRoot: Path,
        classpath: List<File>,
    ): BuildResult
}
