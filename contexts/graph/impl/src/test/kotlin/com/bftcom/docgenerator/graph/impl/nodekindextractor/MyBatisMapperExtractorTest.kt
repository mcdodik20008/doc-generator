package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MyBatisMapperExtractorTest {
    private val extractor = MyBatisMapperExtractor()

    @Test
    fun `id returns mybatis-mapper`() {
        assertThat(extractor.id()).isEqualTo("mybatis-mapper")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue()
        assertThat(extractor.supports(Lang.java)).isFalse()
        assertThat(extractor.supports(Lang.sql)).isFalse()
    }

    @Test
    fun `refineType returns MAPPER for Mapper annotation`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.apache.ibatis.annotations.Mapper")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MAPPER)
    }

    @Test
    fun `refineType returns MAPPER for class in mapper package`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.mapper"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MAPPER)
    }

    @Test
    fun `refineType returns MAPPER for name ending with Mapper`() {
        val raw = createRawType(
            simpleName = "UserMapper",
            pkgFqn = "com.example"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MAPPER)
    }

    @Test
    fun `refineType returns null for regular class`() {
        val raw = createRawType(
            simpleName = "RegularClass",
            pkgFqn = "com.example"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive annotation matching`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.apache.ibatis.annotations.MAPPER")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MAPPER)
    }

    @Test
    fun `refineType handles case-insensitive name matching`() {
        val raw = createRawType(
            simpleName = "USERMAPPER",
            pkgFqn = "com.example"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MAPPER)
    }

    @Test
    fun `refineType returns MAPPER when both annotation and package match`() {
        val raw = createRawType(
            simpleName = "UserMapper",
            pkgFqn = "com.example.mapper",
            annotationsRepr = listOf("org.apache.ibatis.annotations.Mapper")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.MAPPER)
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
