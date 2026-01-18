package com.bftcom.docgenerator.graph.impl.library

import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.library.Library
import com.bftcom.docgenerator.domain.library.LibraryNode
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LibraryNodeIndexImplTest {
    @Test
    fun `buildIndex indexes methods and resolves lookups`() {
        val repo = mockk<LibraryNodeRepository>()
        val methodNode = createLibraryNode(
            fqn = "com.example.Client.call",
            kind = NodeKind.METHOD,
            meta = mapOf("integrationAnalysis" to mapOf("isParentClient" to true)),
        )
        val methodNoDot = createLibraryNode(fqn = "methodOnly", kind = NodeKind.METHOD)
        val classNode = createLibraryNode(fqn = "com.example.Client", kind = NodeKind.CLASS)
        every { repo.findAll() } returns listOf(methodNode, methodNoDot, classNode)

        val index = LibraryNodeIndexImpl(repo)
        index.buildIndex()

        assertThat(index.findByMethodFqn("com.example.Client.call")).isEqualTo(methodNode)
        assertThat(index.findByClassAndMethod("com.example.Client", "call")).isEqualTo(methodNode)
        assertThat(index.findByMethodFqn("methodOnly")).isEqualTo(methodNoDot)
        assertThat(index.findByClassAndMethod("methodOnly", "call")).isNull()
        assertThat(index.isParentClient("com.example.Client.call")).isTrue()
        assertThat(index.isParentClient("methodOnly")).isFalse()
    }

    private fun createLibraryNode(
        fqn: String,
        kind: NodeKind,
        meta: Map<String, Any> = emptyMap(),
    ): LibraryNode {
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
            kind = kind,
            lang = Lang.kotlin,
            meta = meta,
        )
    }
}
