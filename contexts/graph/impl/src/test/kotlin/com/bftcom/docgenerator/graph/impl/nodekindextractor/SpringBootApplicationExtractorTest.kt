package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SpringBootApplicationExtractorTest {
    private val extractor = SpringBootApplicationExtractor()

    @Test
    fun `id returns spring-boot-application`() {
        assertThat(extractor.id()).isEqualTo("spring-boot-application")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue()
        assertThat(extractor.supports(Lang.java)).isFalse()
        assertThat(extractor.supports(Lang.sql)).isFalse()
    }

    @Test
    fun `refineType returns SERVICE for SpringBootApplication annotation`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.boot.autoconfigure.SpringBootApplication")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SERVICE)
    }

    @Test
    fun `refineType returns null for class without SpringBootApplication annotation`() {
        val raw = createRawType(
            annotationsRepr = emptyList()
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType returns null for class with other annotations`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.stereotype.Service")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive annotation matching`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.boot.autoconfigure.SPRINGBOOTAPPLICATION")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SERVICE)
    }

    @Test
    fun `refineType returns SERVICE regardless of base NodeKind`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.boot.autoconfigure.SpringBootApplication")
        )
        val ctx = createContext()

        val result1 = extractor.refineType(NodeKind.CLASS, raw, ctx)
        val result2 = extractor.refineType(NodeKind.INTERFACE, raw, ctx)
        val result3 = extractor.refineType(NodeKind.ENUM, raw, ctx)

        assertThat(result1).isEqualTo(NodeKind.SERVICE)
        assertThat(result2).isEqualTo(NodeKind.SERVICE)
        assertThat(result3).isEqualTo(NodeKind.SERVICE)
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
