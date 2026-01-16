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

class EndpointClassExtractorTest {
    private val extractor = EndpointClassExtractor()

    @Test
    fun `id returns endpoint-class`() {
        assertThat(extractor.id()).isEqualTo("endpoint-class")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
    }

    @ParameterizedTest
    @CsvSource(
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Controller"
    )
    fun `refineType returns ENDPOINT for RestController or Controller annotation`(annotation: String) {
        val raw = createRawType(annotationsRepr = listOf(annotation))
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.ENDPOINT)
    }

    @Test
    fun `refineType returns ENDPOINT for GrpcService annotation`() {
        val raw = createRawType(
            annotationsRepr = listOf("net.devh.boot.grpc.server.service.GrpcService")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.ENDPOINT)
    }

    @Test
    fun `refineType returns null for regular class`() {
        val raw = createRawType(
            simpleName = "RegularClass",
            annotationsRepr = emptyList()
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive annotations`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.web.bind.annotation.RESTCONTROLLER")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.ENDPOINT)
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
