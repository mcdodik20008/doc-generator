package com.bftcom.docgenerator.graph.impl.library

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.library.Library
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.domain.node.NodeMeta
import com.bftcom.docgenerator.domain.node.RawUsage
import com.bftcom.docgenerator.graph.api.library.LibraryNodeIndex
import com.bftcom.docgenerator.library.api.integration.IntegrationPoint
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LibraryNodeEnricherImplTest {
    @Test
    fun `enrichNodeMeta returns meta unchanged for non-method nodes`() {
        val index = mockk<LibraryNodeIndex>()
        val integrationService = mockk<IntegrationPointService>()
        val enricher = LibraryNodeEnricherImpl(index, integrationService)
        val node = createNode(kind = NodeKind.CLASS)
        val meta = NodeMeta(rawUsages = listOf(RawUsage.Simple(name = "call", isCall = true)))

        val result = enricher.enrichNodeMeta(node, meta)

        assertThat(result).isSameAs(meta)
        verify(exactly = 0) { index.findByMethodFqn(any()) }
        verify(exactly = 0) { integrationService.extractIntegrationPoints(any()) }
    }

    @Test
    fun `enrichNodeMeta resolves ownerFqn usages and extracts integration points`() {
        val index = mockk<LibraryNodeIndex>()
        val integrationService = mockk<IntegrationPointService>()
        val enricher = LibraryNodeEnricherImpl(index, integrationService)
        val node = createNode(kind = NodeKind.METHOD)
        val meta = NodeMeta(
            ownerFqn = "com.app.Owner",
            rawUsages = listOf(RawUsage.Simple(name = "call", isCall = true)),
            imports = emptyList(),
        )
        val libraryNode = createLibraryNode("com.lib.Client.call")
        every { index.findByMethodFqn("com.app.Owner.call") } returns libraryNode
        every { integrationService.extractIntegrationPoints(libraryNode) } returns listOf(
            IntegrationPoint.HttpEndpoint(
                url = "https://example.test/api",
                methodId = "com.lib.Client.call",
                httpMethod = "GET",
                clientType = "http",
                hasRetry = true,
            ),
        )

        val result = enricher.enrichNodeMeta(node, meta)

        assertThat(result).isSameAs(meta)
        verify(exactly = 1) { index.findByMethodFqn("com.app.Owner.call") }
        verify(exactly = 1) { integrationService.extractIntegrationPoints(libraryNode) }
    }

    @Test
    fun `enrichNodeMeta resolves fully qualified usage without owner`() {
        val index = mockk<LibraryNodeIndex>()
        val integrationService = mockk<IntegrationPointService>()
        val enricher = LibraryNodeEnricherImpl(index, integrationService)
        val node = createNode(kind = NodeKind.METHOD)
        val meta = NodeMeta(
            ownerFqn = null,
            rawUsages = listOf(RawUsage.Simple(name = "com.lib.Client.call", isCall = true)),
            imports = emptyList(),
        )
        every { index.findByMethodFqn("com.lib.Client.call") } returns null

        val result = enricher.enrichNodeMeta(node, meta)

        assertThat(result).isSameAs(meta)
        verify(exactly = 1) { index.findByMethodFqn("com.lib.Client.call") }
        verify(exactly = 0) { integrationService.extractIntegrationPoints(any()) }
    }

    private fun createNode(kind: NodeKind): Node {
        val app = Application(key = "app", name = "App")
        app.id = 1L
        val node = Node(
            application = app,
            fqn = "com.app.Owner.call",
            name = "call",
            packageName = "com.app",
            kind = kind,
            lang = Lang.kotlin,
        )
        node.id = 1L
        return node
    }

    private fun createLibraryNode(fqn: String): LibraryNode {
        val library = Library(
            coordinate = "com.example:lib:1.0.0",
            groupId = "com.example",
            artifactId = "lib",
            version = "1.0.0",
        )
        library.id = 1L
        return LibraryNode(
            library = library,
            fqn = fqn,
            name = fqn.substringAfterLast('.', missingDelimiterValue = fqn),
            packageName = fqn.substringBeforeLast('.', missingDelimiterValue = ""),
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )
    }
}
