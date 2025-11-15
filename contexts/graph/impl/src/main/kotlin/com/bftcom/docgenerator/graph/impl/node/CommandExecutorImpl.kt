package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.graph.api.declplanner.DeclCmd
import com.bftcom.docgenerator.graph.api.declplanner.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.declplanner.RememberFileUnitCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFieldCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFunctionCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertTypeCmd
import com.bftcom.docgenerator.graph.api.node.CommandExecutor
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.handlers.CommandHandler
import com.bftcom.docgenerator.graph.impl.node.handlers.EnsurePackageHandler
import com.bftcom.docgenerator.graph.impl.node.handlers.RememberFileUnitHandler
import com.bftcom.docgenerator.graph.impl.node.handlers.UpsertFieldHandler
import com.bftcom.docgenerator.graph.impl.node.handlers.UpsertFunctionHandler
import com.bftcom.docgenerator.graph.impl.node.handlers.UpsertTypeHandler
import com.bftcom.docgenerator.graph.impl.node.state.GraphState
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * 
 * Вся логика обработки команд вынесена в отдельные handler'ы.
 */
class CommandExecutorImpl(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val objectMapper: ObjectMapper,
    private val nodeKindRefiner: NodeKindRefiner,
) : CommandExecutor {
    // Состояние сборки графа (создается при каждом execute)
    private val state = GraphState()
    
    // Строитель нод
    private val builder = NodeBuilder(application, nodeRepo, objectMapper)
    
    // Handler'ы для каждой команды
    private val rememberFileUnitHandler = RememberFileUnitHandler()
    private val ensurePackageHandler = EnsurePackageHandler()
    private val upsertTypeHandler = UpsertTypeHandler(nodeKindRefiner)
    private val upsertFieldHandler = UpsertFieldHandler(nodeKindRefiner)
    private val upsertFunctionHandler = UpsertFunctionHandler(nodeKindRefiner)

    override fun execute(cmd: DeclCmd) {
        when (cmd) {
            is RememberFileUnitCmd -> rememberFileUnitHandler.handle(cmd, state, builder)
            is EnsurePackageCmd -> ensurePackageHandler.handle(cmd, state, builder)
            is UpsertTypeCmd -> upsertTypeHandler.handle(cmd, state, builder)
            is UpsertFieldCmd -> upsertFieldHandler.handle(cmd, state, builder)
            is UpsertFunctionCmd -> upsertFunctionHandler.handle(cmd, state, builder)
        }
    }
}
