package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExactSearchStepTest {
    private val nodeRepository = mockk<NodeRepository>()
    private val applicationRepository = mockk<ApplicationRepository>()
    private val step = ExactSearchStep(nodeRepository, applicationRepository)

    @Test
    fun `execute - находит узлы и переходит к GRAPH_EXPANSION`() {
        val app = Application(id = 1L, key = "app", name = "App")
        val node = Node(
            id = 10L,
            application = app,
            fqn = "com.example.UserService.getUser",
            name = "getUser",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )

        every { applicationRepository.findAll() } returns listOf(app)
        every {
            nodeRepository.findByApplicationIdAndClassNameAndMethodName(
                applicationId = 1L,
                className = "UserService",
                methodName = "getUser",
                methodKind = NodeKind.METHOD,
            )
        } returns listOf(node)

        val context = QueryProcessingContext(
            originalQuery = "UserService getUser",
            currentQuery = "UserService getUser",
            sessionId = "s-1",
        ).setMetadata(
            QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
            mapOf("className" to "UserService", "methodName" to "getUser"),
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("HAS_DATA")
        val exactNodes = result.context.getMetadata<List<*>>(QueryMetadataKeys.EXACT_NODES)
        assertThat(exactNodes).isNotNull
        assertThat(exactNodes!!).hasSize(1)
        assertThat((exactNodes[0] as Node).id).isEqualTo(10L)
    }

    @Test
    fun `execute - не находит узлы и переходит к REWRITING`() {
        val app = Application(id = 1L, key = "app", name = "App")

        every { applicationRepository.findAll() } returns listOf(app)
        every {
            nodeRepository.findByApplicationIdAndClassNameAndMethodName(
                any(), any(), any(), any()
            )
        } returns emptyList()

        val context = QueryProcessingContext(
            originalQuery = "NonExistentClass method",
            currentQuery = "NonExistentClass method",
            sessionId = "s-1",
        ).setMetadata(
            QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT,
            mapOf("className" to "NonExistentClass", "methodName" to "method"),
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("NO_DATA")
        assertThat(result.context.hasMetadata(QueryMetadataKeys.EXACT_NODES)).isFalse
    }

    @Test
    fun `execute - нет извлеченных данных и переходит к REWRITING`() {
        val context = QueryProcessingContext(
            originalQuery = "общий запрос",
            currentQuery = "общий запрос",
            sessionId = "s-1",
        )

        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("NO_DATA")
        assertThat(result.context.processingSteps).hasSize(1)
        assertThat(result.context.processingSteps[0].output).contains("Нет извлеченных классов/методов")
    }
}
