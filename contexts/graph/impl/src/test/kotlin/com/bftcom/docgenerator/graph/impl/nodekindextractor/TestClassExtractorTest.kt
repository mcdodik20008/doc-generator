package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TestClassExtractorTest {
    private val extractor = TestClassExtractor()

    @Test
    fun `id returns test-class`() {
        assertThat(extractor.id()).isEqualTo("test-class")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
    }

    @ParameterizedTest
    @CsvSource(
        "UserServiceTest",
        "PaymentControllerIT",
        "OrderRepositorySpec"
    )
    fun `refineType returns TEST for classes ending with Test, IT, or Spec`(className: String) {
        val raw = createRawType(simpleName = className)
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TEST)
    }

    @Test
    fun `refineType returns TEST for package containing test`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.test"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TEST)
    }

    @ParameterizedTest
    @CsvSource(
        "org.junit.jupiter.api.Test",
        "junit.jupiter.api.Test",
        "io.kotest.core.spec.style.DescribeSpec",
        "io.mockk.MockK"
    )
    fun `refineType returns TEST when imports contain test frameworks`(importName: String) {
        val raw = createRawType(simpleName = "SomeClass")
        val ctx = createContext(imports = listOf(importName))

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TEST)
    }

    @Test
    fun `refineType returns null for regular class without test indicators`() {
        val raw = createRawType(
            simpleName = "RegularClass",
            pkgFqn = "com.example.service"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive name matching`() {
        val raw = createRawType(simpleName = "usertest")
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.TEST)
    }

    private fun createRawType(
        simpleName: String = "TestClass",
        pkgFqn: String? = "com.example",
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            simpleName = simpleName,
            kindRepr = "class",
            supertypesRepr = emptyList(),
            annotationsRepr = emptyList(),
            span = null,
            text = null,
        )
    }

    private fun createContext(imports: List<String>? = null): NodeKindContext {
        return NodeKindContext(
            lang = Lang.kotlin,
            file = null,
            imports = imports,
        )
    }
}
