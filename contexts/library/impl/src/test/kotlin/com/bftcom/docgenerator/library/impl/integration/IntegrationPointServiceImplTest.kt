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
    fun `extractIntegrationPoints - возвращает пустой список когда метаданных нет`() {
        val repo = mockk<LibraryNodeRepository>(relaxed = true)
        val service = IntegrationPointServiceImpl(repo)

        val lib = Library(coordinate = "c", groupId = "g", artifactId = "a", version = "1")
        val node =
            LibraryNode(
                library = lib,
                fqn = "com.example.Client.call",
                kind = NodeKind.METHOD,
                lang = Lang.java,
                meta = emptyMap(),
            )

        val points = service.extractIntegrationPoints(node)
        assertThat(points).isEmpty()
    }

    @Test
    fun `extractIntegrationPoints - httpMethods пустой создает endpoint с null методом`() {
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
                                "httpMethods" to emptyList<String>(),
                            ),
                    ),
            )

        val points = service.extractIntegrationPoints(node)
        val http = points.filterIsInstance<IntegrationPoint.HttpEndpoint>()
        assertThat(http).hasSize(1)
        assertThat(http.first().httpMethod).isNull()
        assertThat(http.first().hasRetry).isFalse()
        assertThat(http.first().hasTimeout).isFalse()
        assertThat(http.first().hasCircuitBreaker).isFalse()
    }

    @Test
    fun `extractIntegrationPoints - kafka и camel используют дефолты если метаданные вызовов отсутствуют`() {
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
                                "kafkaTopics" to listOf("t1"),
                                "kafkaCalls" to emptyList<Map<String, Any>>(),
                                "camelUris" to listOf("direct:in"),
                                "camelCalls" to emptyList<Map<String, Any>>(),
                            ),
                    ),
            )

        val points = service.extractIntegrationPoints(node)
        val kafka = points.filterIsInstance<IntegrationPoint.KafkaTopic>().first()
        val camel = points.filterIsInstance<IntegrationPoint.CamelRoute>().first()

        assertThat(kafka.operation).isEqualTo("UNKNOWN")
        assertThat(kafka.clientType).isEqualTo("Unknown")
        assertThat(camel.direction).isEqualTo("UNKNOWN")
        assertThat(camel.endpointType).isNull()
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
    fun `findMethodsByKafkaTopic - оборачивает topic в JSON строку`() {
        val repo = mockk<LibraryNodeRepository>(relaxed = true)
        val service = IntegrationPointServiceImpl(repo)

        every { repo.findMethodsByKafkaTopic(any(), any()) } returns emptyList()
        service.findMethodsByKafkaTopic("topic-1", libraryId = 2L)

        verify(exactly = 1) { repo.findMethodsByKafkaTopic("\"topic-1\"", 2L) }
    }

    @Test
    fun `findMethodsByCamelUri - оборачивает uri в JSON строку`() {
        val repo = mockk<LibraryNodeRepository>(relaxed = true)
        val service = IntegrationPointServiceImpl(repo)

        every { repo.findMethodsByCamelUri(any(), any()) } returns emptyList()
        service.findMethodsByCamelUri("direct:in", libraryId = 3L)

        verify(exactly = 1) { repo.findMethodsByCamelUri("\"direct:in\"", 3L) }
    }

    @Test
    fun `getMethodIntegrationSummary - возвращает null если нода не найдена`() {
        val repo = mockk<LibraryNodeRepository>()
        every { repo.findByLibraryIdAndFqn(1L, "x") } returns null

        val service = IntegrationPointServiceImpl(repo)
        assertThat(service.getMethodIntegrationSummary("x", 1L)).isNull()
    }

    @Test
    fun `getMethodIntegrationSummary - собирает флаги и точки интеграции`() {
        val repo = mockk<LibraryNodeRepository>()
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
                                "isParentClient" to true,
                                "hasRetry" to true,
                                "hasTimeout" to true,
                                "hasCircuitBreaker" to false,
                                "urls" to listOf("/api"),
                                "httpMethods" to listOf("GET"),
                            ),
                    ),
            )

        every { repo.findByLibraryIdAndFqn(10L, "com.example.Client.call") } returns node

        val summary = service.getMethodIntegrationSummary("com.example.Client.call", 10L)

        assertThat(summary?.isParentClient).isTrue()
        assertThat(summary?.hasRetry).isTrue()
        assertThat(summary?.hasTimeout).isTrue()
        assertThat(summary?.hasCircuitBreaker).isFalse()
        assertThat(summary?.httpEndpoints).hasSize(1)
    }
}

