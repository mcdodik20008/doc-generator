package com.bftcom.docgenerator.library.impl.integration

import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.library.Library
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.library.api.integration.IntegrationPoint
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IntegrationPointServiceImplTest {
    @Test
    fun `extractIntegrationPoints - собирает http kafka camel`() {
        val repo = mockk<LibraryNodeRepository>(relaxed = true)
        val service = IntegrationPointServiceImpl(repo)

        val lib = Library(coordinate = "c", groupId = "g", artifactId = "a", version = "1")
        val node =
            LibraryNode(
                library = lib,
                fqn = "com.example.Client.call",
                kind = NodeKind.METHOD,
                lang = Lang.java,
                meta =
                    mapOf(
                        "integrationAnalysis" to
                            mapOf(
                                "urls" to listOf("/api"),
                                "httpMethods" to listOf("GET"),
                                "kafkaTopics" to listOf("t1"),
                                "kafkaCalls" to listOf(mapOf("topic" to "t1", "operation" to "PRODUCE", "clientType" to "KafkaProducer")),
                                "camelUris" to listOf("kafka:t2"),
                                "camelCalls" to listOf(mapOf("uri" to "kafka:t2", "endpointType" to "kafka", "direction" to "FROM")),
                                "hasRetry" to true,
                            ),
                    ),
            )

        val points = service.extractIntegrationPoints(node)
        assertThat(points.filterIsInstance<IntegrationPoint.HttpEndpoint>()).hasSize(1)
        assertThat(points.filterIsInstance<IntegrationPoint.KafkaTopic>()).hasSize(1)
        assertThat(points.filterIsInstance<IntegrationPoint.CamelRoute>()).hasSize(1)
    }

    @Test
    fun `findMethodsByUrl - оборачивает url в JSON строку`() {
        val repo = mockk<LibraryNodeRepository>(relaxed = true)
        val service = IntegrationPointServiceImpl(repo)

        every { repo.findMethodsByUrl(any(), any()) } returns emptyList()
        service.findMethodsByUrl("/api", libraryId = 1L)

        verify(exactly = 1) { repo.findMethodsByUrl("\"/api\"", 1L) }
    }

    @Test
    fun `getMethodIntegrationSummary - возвращает null если нода не найдена`() {
        val repo = mockk<LibraryNodeRepository>()
        every { repo.findByLibraryIdAndFqn(1L, "x") } returns null

        val service = IntegrationPointServiceImpl(repo)
        assertThat(service.getMethodIntegrationSummary("x", 1L)).isNull()
    }
}

