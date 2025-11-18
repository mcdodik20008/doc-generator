package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadataExtractor
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import com.bftcom.docgenerator.graph.impl.util.NkxUtil
import org.springframework.stereotype.Component

/**
 * Извлекает метаданные gRPC endpoint'ов.
 * Поддерживает: @GrpcService, @GrpcMethod
 */
@Component
class GrpcEndpointExtractor : ApiMetadataExtractor {
    override fun id() = "grpc-endpoint"

    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun extractFunctionMetadata(
        function: RawFunction,
        ownerType: RawType?,
        ctx: NodeKindContext,
    ): ApiMetadata.GrpcEndpoint? {
        val anns = NkxUtil.anns(function.annotationsRepr.toList())
        val imps = NkxUtil.imps(ctx.imports)

        // Проверяем наличие gRPC
        val isGrpc =
            NkxUtil.hasAnyAnn(anns, "GrpcMethod") ||
                NkxUtil.importsContain(imps, "io.grpc") ||
                ownerType?.let { NkxUtil.hasAnyAnn(NkxUtil.anns(it.annotationsRepr), "GrpcService") } == true

        if (!isGrpc) return null

        // Извлекаем имя сервиса из типа владельца или пакета
        val serviceName = ownerType?.simpleName ?: extractServiceFromPackage(function.pkgFqn)
        val methodName = function.name
        val packageName = function.pkgFqn

        return ApiMetadata.GrpcEndpoint(
            service = serviceName,
            method = methodName,
            packageName = packageName,
        )
    }

    override fun extractTypeMetadata(
        type: RawType,
        ctx: NodeKindContext,
    ): ApiMetadata? {
        val anns = NkxUtil.anns(type.annotationsRepr)
        val imps = NkxUtil.imps(ctx.imports)

        if (NkxUtil.hasAnyAnn(anns, "GrpcService") ||
            NkxUtil.importsContain(imps, "io.grpc", "net.devh.boot.grpc")
        ) {
            // Это gRPC сервис - базовое метаданное
            return ApiMetadata.GrpcEndpoint(
                service = type.simpleName,
                method = "*", // все методы
                packageName = type.pkgFqn,
            )
        }

        return null
    }

    private fun extractServiceFromPackage(pkg: String?): String {
        // Пытаемся извлечь имя сервиса из пакета: com.example.UserService -> UserService
        return pkg?.substringAfterLast('.') ?: "UnknownService"
    }
}
