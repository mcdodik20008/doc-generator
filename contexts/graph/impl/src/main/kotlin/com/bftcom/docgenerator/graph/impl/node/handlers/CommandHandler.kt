package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.graph.api.declplanner.DeclCmd
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState

/**
 * Интерфейс обработчика команд для построения графа.
 */
interface CommandHandler<in T : DeclCmd> {
    fun handle(
        cmd: T,
        state: GraphState,
        builder: NodeBuilder,
    )
}
