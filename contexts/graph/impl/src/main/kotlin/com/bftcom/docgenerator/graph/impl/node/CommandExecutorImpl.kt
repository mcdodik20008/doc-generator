package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.graph.api.declplanner.DeclCmd
import com.bftcom.docgenerator.graph.api.declplanner.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.declplanner.RememberFileUnitCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFieldCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertFunctionCmd
import com.bftcom.docgenerator.graph.api.declplanner.UpsertTypeCmd
import com.bftcom.docgenerator.graph.api.library.LibraryNodeEnricher
import com.bftcom.docgenerator.graph.api.node.CodeHasher
import com.bftcom.docgenerator.graph.api.node.CodeNormalizer
import com.bftcom.docgenerator.graph.api.node.CommandExecutor
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.api.node.NodeUpdateStrategy
import com.bftcom.docgenerator.graph.api.node.NodeValidator
import com.bftcom.docgenerator.graph.impl.apimetadata.ApiMetadataCollector
import com.bftcom.docgenerator.graph.impl.node.builder.NodeBuilder
import com.bftcom.docgenerator.graph.impl.node.handlers.EnsurePackageHandler
import com.bftcom.docgenerator.graph.impl.node.handlers.RememberFileUnitHandler
import com.bftcom.docgenerator.graph.impl.node.handlers.UpsertFieldHandler
import com.bftcom.docgenerator.graph.impl.node.handlers.UpsertFunctionHandler
import com.bftcom.docgenerator.graph.impl.node.handlers.UpsertTypeHandler
import com.bftcom.docgenerator.graph.impl.node.state.GraphState
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory

/**
 *
 * Вся логика обработки команд вынесена в отдельные handler'ы.
 */
class CommandExecutorImpl(
    private val application: Application,
    private val nodeRepo: NodeRepository,
    private val objectMapper: ObjectMapper,
    private val nodeKindRefiner: NodeKindRefiner,
    private val validator: NodeValidator,
    private val codeNormalizer: CodeNormalizer,
    private val codeHasher: CodeHasher,
    private val updateStrategy: NodeUpdateStrategy,
    private val apiMetadataCollector: ApiMetadataCollector? = null,
    private val libraryNodeEnricher: LibraryNodeEnricher? = null,
) : CommandExecutor {
    private val log = LoggerFactory.getLogger(javaClass)

    // Состояние сборки графа (создается при каждом execute)
    private val state = GraphState()

    // Строитель нод
    val builder =
        NodeBuilder(
            application = application,
            nodeRepo = nodeRepo,
            objectMapper = objectMapper,
            validator = validator,
            codeNormalizer = codeNormalizer,
            codeHasher = codeHasher,
            updateStrategy = updateStrategy,
        )

    // Handler'ы для каждой команды
    private val rememberFileUnitHandler = RememberFileUnitHandler()
    private val ensurePackageHandler = EnsurePackageHandler()
    private val upsertTypeHandler = UpsertTypeHandler(nodeKindRefiner, apiMetadataCollector)
    private val upsertFieldHandler = UpsertFieldHandler(nodeKindRefiner)
    private val upsertFunctionHandler = UpsertFunctionHandler(nodeKindRefiner, apiMetadataCollector, libraryNodeEnricher)

    override fun execute(cmd: DeclCmd) {
        try {
            when (cmd) {
                is RememberFileUnitCmd -> rememberFileUnitHandler.handle(cmd, state, builder)
                is EnsurePackageCmd -> ensurePackageHandler.handle(cmd, state, builder)
                is UpsertTypeCmd -> upsertTypeHandler.handle(cmd, state, builder)
                is UpsertFieldCmd -> upsertFieldHandler.handle(cmd, state, builder)
                is UpsertFunctionCmd -> upsertFunctionHandler.handle(cmd, state, builder)
            }
        } catch (e: Exception) {
            log.error("Failed to execute command: type={}, error={}", cmd::class.simpleName, e.message, e)
            throw e
        }
    }

    /**
     * Получить статистику операций NodeBuilder.
     */
    fun getBuilderStats() = builder.getStats()
}
