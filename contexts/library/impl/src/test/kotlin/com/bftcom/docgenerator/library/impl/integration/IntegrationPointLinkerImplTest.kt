package com.bftcom.docgenerator.library.impl.integration

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.library.Library
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.RawUsage
import com.bftcom.docgenerator.shared.testing.TestObjectMapperFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class IntegrationPointLinkerImplTest {
    private val testMapper = TestObjectMapperFactory.create()

    @Test
    fun `linkIntegrationPoints - связывает метод приложения с Kafka topic через цепочку библиотеки`() {
        val nodeRepo = mockk<NodeRepository>()
        val libNodeRepo = mockk<LibraryNodeRepository>()
        val edgeRepo = mockk<EdgeRepository>(relaxed = true)
        val integrationService = IntegrationPointServiceImpl(libNodeRepo)

        val linker = IntegrationPointLinkerImpl(nodeRepo, libNodeRepo, edgeRepo, integrationService, testMapper)
        val app = Application(id = 1L, key = "app", name = "App")

        val appMethod = Node(
            id = 10L,
            application = app,
            fqn = "com.app.Service.doWork",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            meta = mapOf(
                "pkgFqn" to "com.app.service",
                "ownerFqn" to "com.lib.Client",
                "imports" to listOf("com.lib.Client"),
                "rawUsages" to listOf(
                    mapOf(
                        "name" to "call",
                        "isCall" to true,
                        "@type" to "simple"
                    )
                )
            )
        )

        val lib = Library(id = 1L, coordinate = "c", groupId = "g", artifactId = "a", version = "1")
        val libEntry =
            LibraryNode(
                id = 101L,
                library = lib,
                fqn = "com.lib.Client.call",
                kind = NodeKind.METHOD,
                lang = Lang.java,
                meta = mapOf("internalCalls" to listOf("com.lib.Internal.send")),
            )
        val libKafka =
            LibraryNode(
                id = 102L,
                library = lib,
                fqn = "com.lib.Internal.send",
                kind = NodeKind.METHOD,
                lang = Lang.java,
                meta =
                    mapOf(
                        "integrationAnalysis" to
                                mapOf(
                                    "kafkaTopics" to listOf("orders"),
                                    "kafkaCalls" to
                                            listOf(
                                                mapOf(
                                                    "topic" to "orders",
                                                    "operation" to "PRODUCE",
                                                    "clientType" to "KafkaProducer",
                                                ),
                                            ),
                                ),
                    ),
            )

        every {
            nodeRepo.findPageAllByApplicationIdAndKindIn(1L, setOf(NodeKind.METHOD), any())
        } returns PageImpl(listOf(appMethod))
        every { nodeRepo.findByApplicationIdAndFqn(1L, "com.lib.Client.call") } returns null
        every { nodeRepo.findByApplicationIdAndFqn(1L, "infra:kafka:topic:orders") } returns null

        every { libNodeRepo.findAllByFqn("com.lib.Client.call") } returns listOf(libEntry)
        every { libNodeRepo.findAllByFqn("com.lib.Internal.send") } returns listOf(libKafka)

        val savedNodesSlot = slot<List<Node>>()
        every { nodeRepo.saveAll(capture(savedNodesSlot)) } answers {
            savedNodesSlot.captured.forEachIndexed { idx, node -> node.id = 200L + idx }
            savedNodesSlot.captured
        }

        linker.linkIntegrationPoints(app)

        val savedNode = savedNodesSlot.captured.first()
        assertThat(savedNode.fqn).isEqualTo("infra:kafka:topic:orders")
        assertThat(savedNode.kind).isEqualTo(NodeKind.TOPIC)
        assertThat(savedNode.meta["synthetic"]).isEqualTo(true)
        assertThat(savedNode.meta["origin"]).isEqualTo("linker")

        verify(exactly = 1) { edgeRepo.upsert(10L, 200L, "PRODUCES") }
    }
}

