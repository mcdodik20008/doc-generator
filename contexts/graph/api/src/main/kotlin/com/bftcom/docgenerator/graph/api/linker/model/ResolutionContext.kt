package com.bftcom.docgenerator.graph.api.linker.model

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.node.Node

interface ResolutionContext {
    val application: Application
    val filePath: String?
    val imports: List<String>
    val currentNode: Node?
}
