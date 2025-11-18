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
 * Извлекает метаданные HTTP endpoint'ов из Spring аннотаций.
 * Поддерживает: @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping, @RequestMapping
 */
@Component
class HttpEndpointExtractor : ApiMetadataExtractor {
    override fun id() = "http-endpoint"

    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    override fun extractFunctionMetadata(
        function: RawFunction,
        ownerType: RawType?,
        ctx: NodeKindContext,
    ): ApiMetadata? {
        // Нужен доступ к PSI для парсинга аннотаций, но в RawFunction только строки
        // Пока работаем с аннотациями как со строками и парсим текстово
        return extractFromAnnotations(function.annotationsRepr, ownerType?.annotationsRepr.orEmpty())
    }

    override fun extractTypeMetadata(
        type: RawType,
        ctx: NodeKindContext,
    ): ApiMetadata? {
        // @RequestMapping на классе - это basePath
        val basePath = extractBasePath(type.annotationsRepr)
        return basePath?.let {
            // Возвращаем базовый путь, но это будет использовано для методов
            ApiMetadata.HttpEndpoint(
                method = "*", // все методы
                path = it,
                basePath = it,
            )
        }
    }

    private fun extractFromAnnotations(
        methodAnnotations: Set<String>,
        classAnnotations: List<String>,
    ): ApiMetadata.HttpEndpoint? {
        val anns = NkxUtil.anns(methodAnnotations.toList() + classAnnotations)

        // Определяем HTTP метод
        val method =
            when {
                NkxUtil.hasAnyAnn(anns, "GetMapping") -> "GET"
                NkxUtil.hasAnyAnn(anns, "PostMapping") -> "POST"
                NkxUtil.hasAnyAnn(anns, "PutMapping") -> "PUT"
                NkxUtil.hasAnyAnn(anns, "DeleteMapping") -> "DELETE"
                NkxUtil.hasAnyAnn(anns, "PatchMapping") -> "PATCH"
                NkxUtil.hasAnyAnn(anns, "RequestMapping") -> extractMethodFromRequestMapping(methodAnnotations)
                else -> return null
            }

        // Извлекаем путь - нужно парсить текст аннотации
        // TODO: пока упрощенная версия, потом добавим полный парсинг PSI
        val path = extractPathFromAnnotations(methodAnnotations, classAnnotations) ?: "/"
        val basePath = extractBasePath(classAnnotations)

        return ApiMetadata.HttpEndpoint(
            method = method,
            path = path,
            basePath = basePath,
        )
    }

    private fun extractMethodFromRequestMapping(annotations: Set<String>): String {
        // @RequestMapping(method = [RequestMethod.GET]) -> "GET"
        // TODO: парсить метод из аннотации
        return "GET" // fallback
    }

    private fun extractPathFromAnnotations(
        methodAnnotations: Set<String>,
        classAnnotations: List<String>,
    ): String? {
        // Ищем path в аннотациях - пока упрощенная версия
        // TODO: полный парсинг из PSI
        for (ann in methodAnnotations) {
            // Простой поиск строки в скобках: @GetMapping("/api/users")
            val match = """"([^"]+)"""".toRegex().find(ann)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    private fun extractBasePath(classAnnotations: List<String>): String? {
        // @RequestMapping("/api/v1") на классе
        for (ann in classAnnotations) {
            val match = """"([^"]+)"""".toRegex().find(ann)
            if (match != null) return match.groupValues[1]
        }
        return null
    }
}
