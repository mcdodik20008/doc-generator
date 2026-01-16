package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class HttpEndpointExtractorTest {
    private val extractor = HttpEndpointExtractor()

    @Test
    fun `id returns http-endpoint`() {
        assertThat(extractor.id()).isEqualTo("http-endpoint")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
    }

    @ParameterizedTest
    @CsvSource(
        "GetMapping, GET",
        "PostMapping, POST",
        "PutMapping, PUT",
        "DeleteMapping, DELETE",
        "PatchMapping, PATCH"
    )
    fun `extractFunctionMetadata returns HttpEndpoint for Spring mapping annotations`(annotation: String, expectedMethod: String) {
        // Используем короткое имя, которое чаще всего ожидает экстрактор
        val annotationString = "@$annotation(\"/api/users\")"

        val function = createRawFunction(
            annotationsRepr = setOf(annotationString)
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result)
            .withFailMessage("Extractor returned null for short annotation name: $annotationString. If this fails, please show the HttpEndpointExtractor code.")
            .isNotNull

        assertThat(result).isInstanceOf(ApiMetadata.HttpEndpoint::class.java)
        val httpEndpoint = result as ApiMetadata.HttpEndpoint
        assertThat(httpEndpoint.method).isEqualTo(expectedMethod)
        assertThat(httpEndpoint.path).isEqualTo("/api/users")
    }

    @Test
    fun `extractFunctionMetadata extracts path from annotation`() {
        val function = createRawFunction(
            annotationsRepr = setOf("""org.springframework.web.bind.annotation.GetMapping("/api/v1/users")""")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.HttpEndpoint::class.java)
        val httpEndpoint = result as ApiMetadata.HttpEndpoint
        assertThat(httpEndpoint.path).isEqualTo("/api/v1/users")
    }

        @Test
        fun `extractFunctionMetadata uses default path when not found`() {
            val function = createRawFunction(
                annotationsRepr = setOf("org.springframework.web.bind.annotation.GetMapping")
            )
            val ctx = createContext()

            val result = extractor.extractFunctionMetadata(function, null, ctx)

            assertThat(result).isInstanceOf(ApiMetadata.HttpEndpoint::class.java)
            val httpEndpoint = result as ApiMetadata.HttpEndpoint
            assertThat(httpEndpoint.path).isEqualTo("/")
        }

    @Test
    fun `extractFunctionMetadata extracts basePath from owner type`() {
        val function = createRawFunction(
            annotationsRepr = setOf("""org.springframework.web.bind.annotation.GetMapping("/users")""")
        )
        val ownerType = createRawType(
            annotationsRepr = listOf("""org.springframework.web.bind.annotation.RequestMapping("/api/v1")""")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, ownerType, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.HttpEndpoint::class.java)
        val httpEndpoint = result as ApiMetadata.HttpEndpoint
        assertThat(httpEndpoint.basePath).isEqualTo("/api/v1")
        assertThat(httpEndpoint.path).isEqualTo("/users")
    }

    @Test
    fun `extractFunctionMetadata returns null for non-HTTP annotations`() {
        val function = createRawFunction(
            annotationsRepr = setOf("org.springframework.stereotype.Service")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `extractTypeMetadata returns HttpEndpoint for RequestMapping on class`() {
        val type = createRawType(
            annotationsRepr = listOf("""org.springframework.web.bind.annotation.RequestMapping("/api/v1")""")
        )
        val ctx = createContext()

        val result = extractor.extractTypeMetadata(type, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.HttpEndpoint::class.java)
        val httpEndpoint = result as ApiMetadata.HttpEndpoint
        assertThat(httpEndpoint.method).isEqualTo("*")
        assertThat(httpEndpoint.path).isEqualTo("/api/v1")
        assertThat(httpEndpoint.basePath).isEqualTo("/api/v1")
    }

    @Test
    fun `extractTypeMetadata returns null when no RequestMapping on class`() {
        val type = createRawType(
            annotationsRepr = emptyList()
        )
        val ctx = createContext()

        val result = extractor.extractTypeMetadata(type, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `extractFunctionMetadata handles RequestMapping annotation`() {
        val function = createRawFunction(
            annotationsRepr = setOf("org.springframework.web.bind.annotation.RequestMapping")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.HttpEndpoint::class.java)
        val httpEndpoint = result as ApiMetadata.HttpEndpoint
        assertThat(httpEndpoint.method).isEqualTo("GET") // fallback
    }

    private fun createRawFunction(
        annotationsRepr: Set<String> = emptySet(),
    ): RawFunction {
        return RawFunction(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = "com.example",
            ownerFqn = null,
            name = "testMethod",
            signatureRepr = "fun testMethod()",
            paramNames = emptyList(),
            annotationsRepr = annotationsRepr,
            rawUsages = emptyList(),
            throwsRepr = null,
            kdoc = null,
            span = null,
            text = null,
        )
    }

    private fun createRawType(
        annotationsRepr: List<String> = emptyList(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = "com.example",
            simpleName = "TestController",
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
