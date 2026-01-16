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

class ConfigExtractorTest {
    private val extractor = ConfigExtractor()

    @Test
    fun `id returns config-class`() {
        assertThat(extractor.id()).isEqualTo("config-class")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
        assertThat(extractor.supports(Lang.sql)).isFalse
    }

    @Test
    fun `refineType returns CONFIG for ConfigurationProperties annotation`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.boot.context.properties.ConfigurationProperties")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.CONFIG)
    }

    @Test
    fun `refineType returns CONFIG for Configuration annotation`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.context.annotation.Configuration")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.CONFIG)
    }

    @ParameterizedTest
    @CsvSource(
        "AppConfig, com.example",
        "MyConfiguration, com.example",
        "DatabaseProperties, com.example"
    )
    fun `refineType returns CONFIG for classes with config naming`(className: String, pkg: String) {
        val raw = createRawType(
            simpleName = className,
            pkgFqn = pkg
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.CONFIG)
    }

    @Test
    fun `refineType returns CONFIG for package containing config`() {
        val raw = createRawType(
            simpleName = "SomeClass",
            pkgFqn = "com.example.config"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.CONFIG)
    }

    @Test
    fun `refineType returns null for regular class`() {
        val raw = createRawType(
            simpleName = "RegularClass",
            pkgFqn = "com.example.service"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive annotations`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.boot.context.properties.CONFIGURATIONPROPERTIES")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.CONFIG)
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
