package com.bftcom.docgenerator.graph.api.linker.indexing

import com.bftcom.docgenerator.domain.node.Node
import org.jetbrains.kotlin.fir.resolve.calls.ResolutionContext

interface SymbolResolver {
    fun resolve(
        symbol: String,
        context: ResolutionContext,
    ): Node?
}
