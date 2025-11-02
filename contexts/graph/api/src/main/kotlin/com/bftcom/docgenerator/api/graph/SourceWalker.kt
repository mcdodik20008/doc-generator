package com.bftcom.docgenerator.api.graph

import java.io.File
import java.nio.file.Path

interface SourceWalker {
    fun walk(
        root: Path,
        visitor: SourceVisitor,
        classpath: List<File>,
    )
}
