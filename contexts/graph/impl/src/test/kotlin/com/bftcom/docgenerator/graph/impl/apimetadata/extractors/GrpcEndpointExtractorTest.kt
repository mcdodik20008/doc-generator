package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GrpcEndpointExtractorTest {
    private val extractor = GrpcEndpointExtractor()

    @Test
    fun `id returns grpc-endpoint`() {
        assertThat(extractor.id()).isEqualTo("grpc-endpoint")
    }

    @Test
    fun `supports returns true only for kotlin`() {
        assertThat(extractor.supports(Lang.kotlin)).isTrue
        assertThat(extractor.supports(Lang.java)).isFalse
    }

    @Test
    fun `extractFunctionMetadata returns GrpcEndpoint for GrpcMethod annotation`() {
        val function = createRawFunction(
            annotationsRepr = setOf("net.devh.boot.grpc.server.service.GrpcMethod")
        )
        val ownerType = createRawType(simpleName = "UserService")
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, ownerType, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.GrpcEndpoint::class.java)
        val grpcEndpoint = result as ApiMetadata.GrpcEndpoint
        assertThat(grpcEndpoint.service).isEqualTo("UserService")
        assertThat(grpcEndpoint.method).isEqualTo("testMethod")
    }

    @Test
    fun `extractFunctionMetadata returns GrpcEndpoint when imports contain io grpc`() {
        val function = createRawFunction()
        val ownerType = createRawType(simpleName = "UserService")
        val ctx = createContext(imports = listOf("io.grpc.stub.AbstractStub"))

        val result = extractor.extractFunctionMetadata(function, ownerType, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.GrpcEndpoint::class.java)
    }

    @Test
    fun `extractFunctionMetadata returns GrpcEndpoint when owner type has GrpcService annotation`() {
        val function = createRawFunction()
        val ownerType = createRawType(
            simpleName = "UserService",
            annotationsRepr = listOf("net.devh.boot.grpc.server.service.GrpcService")
        )
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, ownerType, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.GrpcEndpoint::class.java)
        val grpcEndpoint = result as ApiMetadata.GrpcEndpoint
        assertThat(grpcEndpoint.service).isEqualTo("UserService")
    }

    @Test
    fun `extractFunctionMetadata returns null when no gRPC indicators`() {
        val function = createRawFunction()
        val ownerType = createRawType()
        val ctx = createContext()

        val result = extractor.extractFunctionMetadata(function, ownerType, ctx)

        assertThat(result).isNull()
    }

    @Test
    fun `extractFunctionMetadata uses package name when owner type is null`() {
        val function = createRawFunction(
            pkgFqn = "com.example.UserService"
        )
        val ctx = createContext(imports = listOf("io.grpc.stub.AbstractStub"))

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.GrpcEndpoint::class.java)
        val grpcEndpoint = result as ApiMetadata.GrpcEndpoint
        assertThat(grpcEndpoint.service).isEqualTo("UserService")
    }

    @Test
    fun `extractFunctionMetadata uses UnknownService when package is null`() {
        val function = createRawFunction(pkgFqn = null)
        val ctx = createContext(imports = listOf("io.grpc.stub.AbstractStub"))

        val result = extractor.extractFunctionMetadata(function, null, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.GrpcEndpoint::class.java)
        val grpcEndpoint = result as ApiMetadata.GrpcEndpoint
        assertThat(grpcEndpoint.service).isEqualTo("UnknownService")
    }

    @Test
    fun `extractTypeMetadata returns GrpcEndpoint for GrpcService annotation`() {
        val type = createRawType(
            simpleName = "UserService",
            annotationsRepr = listOf("net.devh.boot.grpc.server.service.GrpcService")
        )
        val ctx = createContext()

        val result = extractor.extractTypeMetadata(type, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.GrpcEndpoint::class.java)
        val grpcEndpoint = result as ApiMetadata.GrpcEndpoint
        assertThat(grpcEndpoint.service).isEqualTo("UserService")
        assertThat(grpcEndpoint.method).isEqualTo("*")
    }

    @Test
    fun `extractTypeMetadata returns GrpcEndpoint when imports contain grpc`() {
        val type = createRawType(simpleName = "UserService")
        val ctx = createContext(imports = listOf("io.grpc.stub.AbstractStub"))

        val result = extractor.extractTypeMetadata(type, ctx)

        assertThat(result).isInstanceOf(ApiMetadata.GrpcEndpoint::class.java)
    }

    @Test
    fun `extractTypeMetadata returns null when no gRPC indicators`() {
        val type = createRawType()
        val ctx = createContext()

        val result = extractor.extractTypeMetadata(type, ctx)

        assertThat(result).isNull()
    }

    private fun createRawFunction(
        annotationsRepr: Set<String> = emptySet(),
        pkgFqn: String? = "com.example",
    ): RawFunction {
        return RawFunction(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = pkgFqn,
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
        simpleName: String = "TestClass",
        annotationsRepr: List<String> = emptyList(),
    ): RawType {
        return RawType(
            lang = SrcLang.kotlin,
            filePath = "Test.kt",
            pkgFqn = "com.example",
            simpleName = simpleName,
            kindRepr = "class",
            supertypesRepr = emptyList(),
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
