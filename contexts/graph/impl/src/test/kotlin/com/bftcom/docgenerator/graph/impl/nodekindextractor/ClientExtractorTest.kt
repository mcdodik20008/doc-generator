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

class ClientExtractorTest {
    private val extractor = ClientExtractor()

    @Test
    fun `id returns client-class`() {
        assertThat(extractor.id()).isEqualTo("client-class")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
    }

    @Test
    fun `refineType returns CLIENT for FeignClient annotation`() {
        val raw = createRawType(
            annotationsRepr = listOf("org.springframework.cloud.openfeign.FeignClient")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.INTERFACE, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.CLIENT)
    }

    @ParameterizedTest
    @CsvSource(
        "io.grpc.stub.AbstractStub",
        "net.devh.boot.grpc.client.inject.GrpcClient"
    )
    fun `refineType returns CLIENT when imports contain gRPC packages`(importName: String) {
        val raw = createRawType()
        val ctx = createContext(imports = listOf(importName))

        val result = extractor.refineType(NodeKind.INTERFACE, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.CLIENT)
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
            annotationsRepr = listOf("org.springframework.cloud.openfeign.FEIGNCLIENT")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.INTERFACE, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.CLIENT)
    }

    private fun createRawType(
        simpleName: String = "TestClass",
        pkgFqn: String? = "com.example",
        annotationsRepr: List<String> = emptyList(),
        supertypesRepr: List<String> = emptyList(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
            simpleName = simpleName,
            kindRepr = "interface",
            supertypesRepr = supertypesRepr,
            annotationsRepr = annotationsRepr,
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
