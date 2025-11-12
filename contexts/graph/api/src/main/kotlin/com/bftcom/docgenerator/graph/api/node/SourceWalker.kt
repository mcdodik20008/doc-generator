package com.bftcom.docgenerator.graph.api.node

import java.io.File
import java.nio.file.Path

interface SourceWalker {
    fun walk(
        root: Path,
        visitor: SourceVisitor,
        classpath: List<File>,
    )
}
