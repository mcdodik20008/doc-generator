package com.bftcom.docgenerator.graph.impl.profile

import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.springframework.data.domain.PageRequest

class ArchitectureProfileBuilderTest {
    private val app = Application(id = 1L, key = "test-app", name = "Test App")
    private lateinit var nodeRepo: NodeRepository
    private lateinit var edgeRepo: EdgeRepository
    private lateinit var chunkRepo: ChunkRepository
    private lateinit var builder: ArchitectureProfileBuilder

    @BeforeEach
    fun setUp() {
        nodeRepo = mock(NodeRepository::class.java)
        edgeRepo = mock(EdgeRepository::class.java)
        chunkRepo = mock(ChunkRepository::class.java)
        builder = ArchitectureProfileBuilder(nodeRepo, edgeRepo, chunkRepo, ObjectMapper())
    }

    @Test
    fun `builds profile with layer counts`() {
        val nodes =
            listOf(
                makeNode(1, NodeKind.MODULE, "core"),
                makeNode(2, NodeKind.CLASS, "UserService"),
                makeNode(3, NodeKind.CLASS, "OrderService"),
                makeNode(4, NodeKind.METHOD, "getUser"),
                makeNode(5, NodeKind.ENDPOINT, "POST /users", meta = mapOf("httpMethod" to "POST", "path" to "/users")),
            )
        `when`(nodeRepo.findAllByApplicationId(eq(1L), any())).thenReturn(nodes)
        `when`(edgeRepo.findAllBySrcIdIn(any<Set<Long>>())).thenReturn(emptyList())
        `when`(edgeRepo.findAllByDstIdIn(any())).thenReturn(emptyList())

        val profile = builder.buildProfile(app)

        assertThat(profile).contains("Архитектурный профиль: test-app")
        assertThat(profile).contains("MODULE: 1")
        assertThat(profile).contains("CLASS: 2")
        assertThat(profile).contains("METHOD: 1")
        assertThat(profile).contains("ENDPOINT: 1")
    }

    @Test
    fun `includes API surface with endpoints`() {
        val nodes =
            listOf(
                makeNode(1, NodeKind.ENDPOINT, "getUsers", meta = mapOf("httpMethod" to "GET", "path" to "/api/users")),
                makeNode(2, NodeKind.ENDPOINT, "createUser", meta = mapOf("httpMethod" to "POST", "path" to "/api/users")),
            )
        `when`(nodeRepo.findAllByApplicationId(eq(1L), any())).thenReturn(nodes)
        `when`(edgeRepo.findAllBySrcIdIn(any<Set<Long>>())).thenReturn(emptyList())

        val profile = builder.buildProfile(app)

        assertThat(profile).contains("API (эндпоинты)")
        assertThat(profile).contains("GET /api/users")
        assertThat(profile).contains("POST /api/users")
    }

    @Test
    fun `includes data model with accessors`() {
        val table = makeNode(10, NodeKind.DB_TABLE, "users")
        val service = makeNode(20, NodeKind.CLASS, "UserService")
        val nodes = listOf(table, service)

        val readEdge =
            Edge(
                src = service,
                dst = table,
                kind = EdgeKind.READS,
            )
        `when`(nodeRepo.findAllByApplicationId(eq(1L), any())).thenReturn(nodes)
        `when`(edgeRepo.findAllByDstIdIn(any())).thenReturn(listOf(readEdge))
        `when`(edgeRepo.findAllBySrcIdIn(any<Set<Long>>())).thenReturn(emptyList())

        val profile = builder.buildProfile(app)

        assertThat(profile).contains("Модель данных")
        assertThat(profile).contains("users")
        assertThat(profile).contains("читают: UserService")
    }

    @Test
    fun `persists chunk on REPO node`() {
        val repoNode = makeNode(100, NodeKind.REPO, "test-app-repo")
        `when`(nodeRepo.findAllByApplicationIdAndKindIn(eq(1L), eq(setOf(NodeKind.REPO)), any()))
            .thenReturn(listOf(repoNode))
        `when`(chunkRepo.upsertDocChunk(any(), any(), any(), any(), any(), any())).thenReturn(1)

        builder.persistAsChunk(app, "# Profile content")

        verify(chunkRepo).upsertDocChunk(
            eq(1L),
            eq(100L),
            eq("ru"),
            eq("arch_profile"),
            eq("# Profile content"),
            any(),
        )
    }

    @Test
    fun `empty profile when no nodes`() {
        `when`(nodeRepo.findAllByApplicationId(eq(1L), any())).thenReturn(emptyList())

        val profile = builder.buildProfile(app)

        assertThat(profile).isEmpty()
    }

    private fun makeNode(
        id: Long,
        kind: NodeKind,
        name: String,
        meta: Map<String, Any> = emptyMap(),
    ): Node =
        Node(
            id = id,
            application = app,
            fqn = "com.example.$name",
            name = name,
            kind = kind,
            lang = Lang.KOTLIN,
            meta = meta,
        )
}
