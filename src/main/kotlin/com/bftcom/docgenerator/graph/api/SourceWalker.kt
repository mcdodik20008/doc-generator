package com.bftcom.docgenerator.graph.api

import java.nio.file.Path

interface SourceWalker {
    fun walk(
        root: Path,
        visitor: SourceVisitor,
    )
}
