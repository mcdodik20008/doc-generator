package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ServiceLayerExtractorTest {
    private val extractor = ServiceLayerExtractor()

    @Test
    fun `id returns service-layer`() {
        assertThat(extractor.id()).isEqualTo("service-layer")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
    }

    @Test
    fun `refineType returns SERVICE for Service annotation`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.stereotype.Service")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SERVICE)
    }

    @Test
    fun `refineType returns SERVICE for Component annotation in service package`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.service",
            annotationsRepr = listOf("org.springframework.stereotype.Component")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SERVICE)
    }

    @Test
    fun `refineType returns SERVICE for class in service package with Service suffix`() {
        val raw = createRawType(
            simpleName = "UserService",
            pkgFqn = "com.example.service"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SERVICE)
    }

    @Test
    fun `refineType returns null for Component annotation outside service package`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.controller",
            annotationsRepr = listOf("org.springframework.stereotype.Component")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType returns null for regular class`() {
        val raw = createRawType(
            simpleName = "RegularClass",
            pkgFqn = "com.example.model"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive annotations`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.stereotype.SERVICE")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SERVICE)
    }

    private fun createRawType(
        simpleName: String = "TestClass",
        pkgFqn: String? = "com.example",
        annotationsRepr: List<String> = emptyList(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            simpleName = simpleName,
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = annotationsRepr,
            span = null,
            text = null,
        )
    }

    private fun createContext(): NodeKindContext {
        return NodeKindContext(
            lang = Lang.kotlin,
            file = null,
            imports = null,
        )
    }
}
