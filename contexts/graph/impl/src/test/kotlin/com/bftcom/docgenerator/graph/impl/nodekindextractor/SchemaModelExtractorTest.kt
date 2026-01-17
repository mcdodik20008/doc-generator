package com.bftcom.docgenerator.graph.impl.nodekindextractor

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SchemaModelExtractorTest {
    private val extractor = SchemaModelExtractor()

    @Test
    fun `id returns schema-model`() {
        assertThat(extractor.id()).isEqualTo("schema-model")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue()
        assertThat(extractor.supports(Lang.java)).isFalse()
        assertThat(extractor.supports(Lang.sql)).isFalse()
    }

    @Test
    fun `refineType returns SCHEMA for Schema annotation`() {
        val raw = createRawType(
            annotationsRepr = listOf("io.swagger.v3.oas.annotations.media.Schema")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SCHEMA)
    }

    @Test
    fun `refineType returns SCHEMA for OpenAPI imports`() {
        val raw = createRawType()
        val ctx = createContext(
            imports = listOf("io.swagger.v3.oas.annotations.media.Schema")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SCHEMA)
    }

    @Test
    fun `refineType returns SCHEMA for Avro imports`() {
        val raw = createRawType()
        val ctx = createContext(
            imports = listOf("org.apache.avro.Schema")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SCHEMA)
    }

    @Test
    fun `refineType returns SCHEMA for Confluent imports`() {
        val raw = createRawType()
        val ctx = createContext(
            imports = listOf("io.confluent.kafka.schemaregistry.client.SchemaRegistryClient")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SCHEMA)
    }

    @Test
    fun `refineType returns SCHEMA for class in schema package`() {
        val raw = createRawType(
            pkgFqn = "com.example.schema"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SCHEMA)
    }

    @Test
    fun `refineType returns null for regular class`() {
        val raw = createRawType(
            pkgFqn = "com.example"
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `refineType handles case-insensitive annotation matching`() {
        val raw = createRawType(
            annotationsRepr = listOf("io.swagger.v3.oas.annotations.media.SCHEMA")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SCHEMA)
    }

    @Test
    fun `refineType handles case-insensitive import matching`() {
        val raw = createRawType()
        val ctx = createContext(
            imports = listOf("IO.SWAGGER.V3.OAS.ANNOTATIONS.MEDIA.SCHEMA")
        )

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SCHEMA)
    }

    @Test
    fun `refineType returns SCHEMA when both annotation and package match`() {
        val raw = createRawType(
            pkgFqn = "com.example.schema",
            annotationsRepr = listOf("io.swagger.v3.oas.annotations.media.Schema")
        )
        val ctx = createContext()

        val result = extractor.refineType(NodeKind.CLASS, raw, ctx)

        assertThat(result).isEqualTo(NodeKind.SCHEMA)
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
    fun `refineType returns null for class with other imports`() {
        val raw = createRawType()
        val ctx = createContext(
            imports = listOf("org.springframework.stereotype.Service")
        )

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

    private fun createContext(
        imports: List<String>? = null,
    ): NodeKindContext {
        return NodeKindContext(
            lang = Lang.kotlin,
            file = null,
            imports = imports,
        )
    }
}
