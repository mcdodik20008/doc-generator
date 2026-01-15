package com.bftcom.docgenerator.graph.impl.linker.virtual

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.impl.linker.NodeIndexFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VirtualNodeFactoryTest {
    private lateinit var nodeRepo: NodeRepository
    private lateinit var factory: VirtualNodeFactory
    private val app = Application(id = 1L, key = "app", name = "App")

    @BeforeEach
    fun setUp() {
        nodeRepo = mockk()
        factory = VirtualNodeFactory(nodeRepo)
    }

    @Test
    fun `getOrCreateEndpointNode - если узел уже есть в индексе, не сохраняет`() {
        val existing =
            Node(
                id = 100L,
                application = app,
                fqn = "endpoint://GET /api/users",
                name = "users",
                packageName = null,
                kind = NodeKind.ENDPOINT,
                lang = Lang.java,
            )
        val index = NodeIndexFactory().create(listOf(existing))

        val (node, created) =
            factory.getOrCreateEndpointNode(
                url = "/api/users",
                httpMethod = "GET",
                index = index,
                application = app,
            )

        assertThat(node).isSameAs(existing)
        assertThat(created).isFalse()
        verify(exactly = 0) { nodeRepo.save(any()) }
    }

    @Test
    fun `getOrCreateEndpointNode - создает новый ENDPOINT с правильным fqn и meta`() {
        val index = NodeIndexFactory().create(emptyList())
        every { nodeRepo.save(any()) } answers {
            val n = firstArg<Node>()
            n.id = 101L
            n
        }

        val (node, created) =
            factory.getOrCreateEndpointNode(
                url = "/api/users",
                httpMethod = "GET",
                index = index,
                application = app,
            )

        assertThat(created).isTrue()
        assertThat(node).isNotNull
        assertThat(node!!.fqn).isEqualTo("endpoint://GET /api/users")
        assertThat(node.kind).isEqualTo(NodeKind.ENDPOINT)
        assertThat(node.lang).isEqualTo(Lang.java)
        assertThat(node.meta).containsEntry("url", "/api/users")
        assertThat(node.meta).containsEntry("httpMethod", "GET")
        assertThat(node.meta).containsEntry("source", "library_analysis")
        verify(exactly = 1) { nodeRepo.save(any()) }
    }

    @Test
    fun `getOrCreateEndpointNode - если httpMethod null, кладет UNKNOWN в meta и иной fqn`() {
        val index = NodeIndexFactory().create(emptyList())
        every { nodeRepo.save(any()) } answers { firstArg() }

        val (node, created) =
            factory.getOrCreateEndpointNode(
                url = "/api/users",
                httpMethod = null,
                index = index,
                application = app,
            )

        assertThat(created).isTrue()
        assertThat(node).isNotNull
        assertThat(node!!.fqn).isEqualTo("endpoint:///api/users")
        assertThat(node.meta).containsEntry("httpMethod", "UNKNOWN")
    }

    @Test
    fun `getOrCreateTopicNode - создает новый TOPIC`() {
        val index = NodeIndexFactory().create(emptyList())
        every { nodeRepo.save(any()) } answers { firstArg() }

        val (node, created) =
            factory.getOrCreateTopicNode(
                topic = "orders",
                index = index,
                application = app,
            )

        assertThat(created).isTrue()
        assertThat(node).isNotNull
        assertThat(node!!.fqn).isEqualTo("topic://orders")
        assertThat(node.kind).isEqualTo(NodeKind.TOPIC)
        assertThat(node.lang).isEqualTo(Lang.java)
        assertThat(node.meta).containsEntry("topic", "orders")
        assertThat(node.meta).containsEntry("source", "library_analysis")
    }

    @Test
    fun `getOrCreateTopicNode - если сохранение падает, возвращает null false`() {
        val index = NodeIndexFactory().create(emptyList())
        every { nodeRepo.save(any()) } throws RuntimeException("db down")

        val (node, created) =
            factory.getOrCreateTopicNode(
                topic = "orders",
                index = index,
                application = app,
            )

        assertThat(node).isNull()
        assertThat(created).isFalse()
    }
}

