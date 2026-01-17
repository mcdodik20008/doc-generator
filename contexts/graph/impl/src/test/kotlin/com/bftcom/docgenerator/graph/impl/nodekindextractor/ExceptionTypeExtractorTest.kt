package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ExceptionTypeExtractorTest {
    private val extractor = ExceptionTypeExtractor()

    @Test
    fun `id returns exception-type`() {
        assertThat(extractor.id()).isEqualTo("exception-type")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue()
        assertThat(extractor.supports(Lang.java)).isFalse()
        assertThat(extractor.supports(Lang.sql)).isFalse()
    }

    @Test
    fun `refineType returns EXCEPTION for class extending Throwable`() {
        val raw = createRawType(
            supertypesRepr = listOf("java.lang.Throwable")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.EXCEPTION)
    }

    @Test
    fun `refineType returns EXCEPTION for class extending Exception`() {
        val raw = createRawType(
            supertypesRepr = listOf("java.lang.Exception")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.EXCEPTION)
    }

    @Test
    fun `refineType returns EXCEPTION for class extending RuntimeException`() {
        val raw = createRawType(
            supertypesRepr = listOf("java.lang.RuntimeException")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.EXCEPTION)
    }

    @Test
    fun `refineType returns EXCEPTION for name ending with Exception`() {
        val raw = createRawType(
            simpleName = "MyException"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.EXCEPTION)
    }

    @Test
    fun `refineType returns EXCEPTION for name ending with Error`() {
        val raw = createRawType(
            simpleName = "MyError"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.EXCEPTION)
    }

    @Test
    fun `refineType returns null for regular class`() {
        val raw = createRawType(
            simpleName = "RegularClass"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive supertype matching`() {
        val raw = createRawType(
            supertypesRepr = listOf("java.lang.THROWABLE")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.EXCEPTION)
    }

    @Test
    fun `refineType handles case-insensitive name matching`() {
        val raw = createRawType(
            simpleName = "MYEXCEPTION"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.EXCEPTION)
    }

    @Test
    fun `refineType returns EXCEPTION when both supertype and name match`() {
        val raw = createRawType(
            simpleName = "MyException",
            supertypesRepr = listOf("java.lang.Exception")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.EXCEPTION)
    }

    @Test
    fun `refineType returns null for class extending other types`() {
        val raw = createRawType(
            supertypesRepr = listOf("java.lang.Object")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType returns null for name containing Exception but not ending with it`() {
        val raw = createRawType(
            simpleName = "ExceptionHandler"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    private fun createRawType(
        simpleName: String = "TestClass",
        pkgFqn: String? = "com.example",
        supertypesRepr: List<String> = emptyList(),
        annotationsRepr: List<String> = emptyList(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            simpleName = simpleName,
            kindRepr = "class",
            supertypesRepr = supertypesRepr,
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
