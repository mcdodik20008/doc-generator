package com.bftcom.docgenerator.graph.impl.node.handlers

import com.bftcom.docgenerator.graph.api.declplanner.RememberFileUnitCmd
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.state.GraphState

/**
 * Обработчик команды RememberFileUnitCmd - сохраняет информацию о файле в состояние.
 */
class RememberFileUnitHandler : CommandHandler<RememberFileUnitCmd> {
    override fun handle(
        cmd: RememberFileUnitCmd,
        state: GraphState,
        builder: NodeBuilder,
    ) {
        state.rememberFileUnit(cmd.unit)
    }
}
