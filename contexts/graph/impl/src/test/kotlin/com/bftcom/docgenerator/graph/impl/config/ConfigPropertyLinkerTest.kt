package com.bftcom.docgenerator.graph.impl.config

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.springframework.data.domain.Pageable

class ConfigPropertyLinkerTest {
    private val app = Application(id = 1L, key = "test-app", name = "Test App")
    private lateinit var nodeRepo: NodeRepository
    private lateinit var edgeRepo: EdgeRepository
    private lateinit var linker: ConfigPropertyLinker

    @BeforeEach
    fun setUp() {
        nodeRepo = mock(NodeRepository::class.java)
        edgeRepo = mock(EdgeRepository::class.java)
        linker = ConfigPropertyLinker(nodeRepo, edgeRepo)
    }

    @Test
    fun `links @Value field to INFRASTRUCTURE node`() {
        val infraNode = node(
            id = 10L,
            fqn = "infra:yaml:http:rr.ups-client",
            kind = NodeKind.INFRASTRUCTURE,
            meta = mapOf("configPrefix" to "rr.ups-client", "integrationType" to "HTTP"),
        )

        val fieldNode = node(
            id = 20L,
            fqn = "com.example.MyService.upsClientUrl",
            kind = NodeKind.FIELD,
            sourceCode = """
                @Value("${'$'}{rr.ups-client.api-url}")
                private lateinit var upsClientUrl: String
            """.trimIndent(),
        )

        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.INFRASTRUCTURE)), any<Pageable>()))
            .thenReturn(listOf(infraNode))
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.FIELD, NodeKind.METHOD)), any<Pageable>()))
            .thenReturn(listOf(fieldNode))
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.CONFIG)), any<Pageable>()))
            .thenReturn(emptyList())

        val count = linker.link(app)

        assertThat(count).isEqualTo(1)
        verify(edgeRepo).upsert(20L, 10L, EdgeKind.DEPENDS_ON.name)
    }

    @Test
    fun `links @ConfigurationProperties class to INFRASTRUCTURE node`() {
        val infraNode = node(
            id = 10L,
            fqn = "infra:yaml:http:rr.ups-client",
            kind = NodeKind.INFRASTRUCTURE,
            meta = mapOf("configPrefix" to "rr.ups-client", "integrationType" to "HTTP"),
        )

        val configNode = node(
            id = 30L,
            fqn = "com.example.UpsClientProperties",
            kind = NodeKind.CONFIG,
            sourceCode = """
                @ConfigurationProperties(prefix = "rr.ups-client")
                class UpsClientProperties {
                    var apiUrl: String = ""
                    var timeout: Int = 5000
                }
            """.trimIndent(),
        )

        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.INFRASTRUCTURE)), any<Pageable>()))
            .thenReturn(listOf(infraNode))
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.FIELD, NodeKind.METHOD)), any<Pageable>()))
            .thenReturn(emptyList())
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.CONFIG)), any<Pageable>()))
            .thenReturn(listOf(configNode))

        val count = linker.link(app)

        assertThat(count).isEqualTo(1)
        verify(edgeRepo).upsert(30L, 10L, EdgeKind.CONFIGURES.name)
    }

    @Test
    fun `no link when property key does not match any INFRASTRUCTURE node`() {
        val infraNode = node(
            id = 10L,
            fqn = "infra:yaml:http:rr.ups-client",
            kind = NodeKind.INFRASTRUCTURE,
            meta = mapOf("configPrefix" to "rr.ups-client", "integrationType" to "HTTP"),
        )

        val fieldNode = node(
            id = 20L,
            fqn = "com.example.MyService.someOtherUrl",
            kind = NodeKind.FIELD,
            sourceCode = """
                @Value("${'$'}{some.other.property}")
                private lateinit var someOtherUrl: String
            """.trimIndent(),
        )

        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.INFRASTRUCTURE)), any<Pageable>()))
            .thenReturn(listOf(infraNode))
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.FIELD, NodeKind.METHOD)), any<Pageable>()))
            .thenReturn(listOf(fieldNode))
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.CONFIG)), any<Pageable>()))
            .thenReturn(emptyList())

        val count = linker.link(app)

        assertThat(count).isEqualTo(0)
        verify(edgeRepo, never()).upsert(any(), any(), any())
    }

    @Test
    fun `links @Value with default value to INFRASTRUCTURE node`() {
        val infraNode = node(
            id = 10L,
            fqn = "infra:yaml:database:spring.datasource",
            kind = NodeKind.INFRASTRUCTURE,
            meta = mapOf("configPrefix" to "spring.datasource", "integrationType" to "DATABASE"),
        )

        val fieldNode = node(
            id = 20L,
            fqn = "com.example.DbConfig.dbUrl",
            kind = NodeKind.FIELD,
            sourceCode = """
                @Value("${'$'}{spring.datasource.url:jdbc:postgresql://localhost/db}")
                private lateinit var dbUrl: String
            """.trimIndent(),
        )

        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.INFRASTRUCTURE)), any<Pageable>()))
            .thenReturn(listOf(infraNode))
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.FIELD, NodeKind.METHOD)), any<Pageable>()))
            .thenReturn(listOf(fieldNode))
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.CONFIG)), any<Pageable>()))
            .thenReturn(emptyList())

        val count = linker.link(app)

        assertThat(count).isEqualTo(1)
        verify(edgeRepo).upsert(20L, 10L, EdgeKind.DEPENDS_ON.name)
    }

    @Test
    fun `returns zero when no infrastructure nodes exist`() {
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.INFRASTRUCTURE)), any<Pageable>()))
            .thenReturn(emptyList())

        val count = linker.link(app)

        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `findMatchingInfraNode - exact prefix match`() {
        val infraNode = node(
            id = 10L,
            fqn = "infra:yaml:http:rr.ups-client",
            kind = NodeKind.INFRASTRUCTURE,
        )
        val infraByPrefix = mapOf("rr.ups-client" to infraNode)

        val result = linker.findMatchingInfraNode("rr.ups-client", infraByPrefix)
        assertThat(result).isEqualTo(infraNode)
    }

    @Test
    fun `findMatchingInfraNode - property key is child of prefix`() {
        val infraNode = node(
            id = 10L,
            fqn = "infra:yaml:http:rr.ups-client",
            kind = NodeKind.INFRASTRUCTURE,
        )
        val infraByPrefix = mapOf("rr.ups-client" to infraNode)

        val result = linker.findMatchingInfraNode("rr.ups-client.api-url", infraByPrefix)
        assertThat(result).isEqualTo(infraNode)
    }

    @Test
    fun `findMatchingInfraNode - no match returns null`() {
        val infraNode = node(
            id = 10L,
            fqn = "infra:yaml:http:rr.ups-client",
            kind = NodeKind.INFRASTRUCTURE,
        )
        val infraByPrefix = mapOf("rr.ups-client" to infraNode)

        val result = linker.findMatchingInfraNode("totally.different.key", infraByPrefix)
        assertThat(result).isNull()
    }

    // --- Helpers ---

    private fun node(
        id: Long? = null,
        fqn: String,
        kind: NodeKind,
        sourceCode: String? = null,
        meta: Map<String, Any> = emptyMap(),
    ): Node = Node(
        id = id,
        application = app,
        fqn = fqn,
        name = fqn.substringAfterLast('.'),
        kind = kind,
        lang = if (kind == NodeKind.INFRASTRUCTURE) Lang.yaml else Lang.kotlin,
        sourceCode = sourceCode,
        meta = meta,
    )
}
