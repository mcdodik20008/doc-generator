package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.graph.api.declplanner.DeclPlanner
import com.bftcom.docgenerator.graph.api.linker.GraphLinker
import com.bftcom.docgenerator.graph.api.model.BuildResult
import com.bftcom.docgenerator.graph.api.node.CodeHasher
import com.bftcom.docgenerator.graph.api.node.CodeNormalizer
import com.bftcom.docgenerator.graph.api.node.NodeKindRefiner
import com.bftcom.docgenerator.graph.api.node.NodeUpdateStrategy
import com.bftcom.docgenerator.graph.api.node.NodeValidator
import com.bftcom.docgenerator.graph.impl.apimetadata.ApiMetadataCollector
import com.bftcom.docgenerator.graph.impl.node.KotlinSourceWalker
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class KotlinGraphBuilderTest {
    private lateinit var nodeRepo: NodeRepository
    private lateinit var edgeRepo: EdgeRepository
    private lateinit var kotlinWalker: KotlinSourceWalker
    private lateinit var graphLinker: GraphLinker
    private lateinit var objectMapper: ObjectMapper
    private lateinit var planners: List<DeclPlanner<*>>
    private lateinit var nodeKindRefiner: NodeKindRefiner
    private lateinit var validator: NodeValidator
    private lateinit var codeNormalizer: CodeNormalizer
    private lateinit var codeHasher: CodeHasher
    private lateinit var updateStrategy: NodeUpdateStrategy
    private lateinit var apiMetadataCollector: ApiMetadataCollector

    private lateinit var builder: KotlinGraphBuilder

    private lateinit var txManager: PlatformTransactionManager

    @BeforeEach
    fun setUp() {
        nodeRepo = mockk()
        edgeRepo = mockk()
        kotlinWalker = mockk(relaxed = true)
        graphLinker = mockk(relaxed = true)
        objectMapper = ObjectMapper().registerKotlinModule()
        planners = emptyList()
        nodeKindRefiner = mockk(relaxed = true)
        validator = mockk(relaxed = true)
        codeNormalizer = mockk(relaxed = true)
        codeHasher = mockk(relaxed = true)
        updateStrategy = mockk(relaxed = true)
        apiMetadataCollector = mockk(relaxed = true)
        txManager = mockk {
            // Настраиваем базовое поведение, чтобы возвращался пустой статус
            val status = mockk<TransactionStatus>(relaxed = true)
            every { getTransaction(any()) } returns status
            every { commit(status) } just Runs
            every { rollback(status) } just Runs
        }

        builder =
            KotlinGraphBuilder(
                nodeRepo = nodeRepo,
                edgeRepo = edgeRepo,
                kotlinWalker = kotlinWalker,
                graphLinker = graphLinker,
                transactionManager = txManager,
                objectMapper = objectMapper,
                planners = planners,
                nodeKindRefiner = nodeKindRefiner,
                validator = validator,
                codeNormalizer = codeNormalizer,
                codeHasher = codeHasher,
                updateStrategy = updateStrategy,
                apiMetadataCollector = apiMetadataCollector,
            )
    }

    @Test
    fun `build - вызывает фазы и корректно считает BuildResult`() {
        val app = Application(id = 1L, key = "app", name = "App")
        val root: Path = Path.of(".")
        val classpath: List<File> = emptyList()

        // before -> after
        every { nodeRepo.count() } returnsMany listOf(5L, 7L)
        every { edgeRepo.count() } returnsMany listOf(10L, 15L)

        val result: BuildResult = builder.build(app, root, classpath)

        assertThat(result.nodes).isEqualTo(2)
        assertThat(result.edges).isEqualTo(5)

        verify(exactly = 1) { kotlinWalker.walk(root, any(), classpath) }
        verify(exactly = 1) { graphLinker.link(app) }
        verify(exactly = 2) { nodeRepo.count() }
        verify(exactly = 2) { edgeRepo.count() }
    }
}

