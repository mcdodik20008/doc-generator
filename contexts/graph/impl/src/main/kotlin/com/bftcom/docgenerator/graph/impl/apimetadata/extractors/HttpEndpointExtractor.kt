package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadataExtractor
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.springframework.stereotype.Component

@Component
class HttpEndpointExtractor : ApiMetadataExtractor {
    override fun id() = "http-endpoint"
    override fun supports(lang: Lang) = (lang == Lang.kotlin)

    private val mappingToMethod = mapOf(
        "GetMapping" to "GET",
        "PostMapping" to "POST",
        "PutMapping" to "PUT",
        "DeleteMapping" to "DELETE",
        "PatchMapping" to "PATCH",
        "RequestMapping" to "GET"
    )

    /**
     * Regex 1: Извлекает имя аннотации (группа 1) и содержимое скобок (группа 2).
     * Поддерживает: @org.sfw.GetMapping("/path"), @GetMapping, RequestMapping(value="/")
     */
    private val annPattern = """@?(?:[\w.]+\.)?(\w+)(?:\s*\((.*)\))?""".toRegex()

    /**
     * Regex 2: Ищет строку в кавычках внутри содержимого скобок.
     */
    private val pathPattern = """"([^"]+)"""".toRegex()

    override fun extractFunctionMetadata(
        function: RawFunction,
        ownerType: RawType?,
        ctx: NodeKindContext,
    ): ApiMetadata? {
        val annotations = function.annotationsRepr

        // 1. Ищем подходящую аннотацию
        for (ann in annotations) {
            val match = annPattern.matchEntire(ann) ?: continue
            val annName = match.groupValues[1]
            val method = mappingToMethod[annName] ?: continue

            // 2. Извлекаем путь (если есть скобки и кавычки)
            val parenthesesContent = match.groupValues.getOrNull(2)
            val extractedPath = parenthesesContent?.let {
                pathPattern.find(it)?.groupValues?.get(1)
            }

            // 3. Обработка RequestMethod (для RequestMapping)
            val finalMethod = if (annName == "RequestMapping") {
                extractMethodFromRequestMapping(ann)
            } else method

            val basePath = ownerType?.let { extractPathFromRaw(it.annotationsRepr) }

            return ApiMetadata.HttpEndpoint(
                method = finalMethod,
                path = normalizePath(extractedPath ?: "/"),
                basePath = basePath
            )
        }
        return null
    }

    override fun extractTypeMetadata(type: RawType, ctx: NodeKindContext): ApiMetadata? {
        val path = extractPathFromRaw(type.annotationsRepr) ?: return null
        return ApiMetadata.HttpEndpoint(
            method = "*",
            path = path,
            basePath = path
        )
    }

    private fun extractPathFromRaw(annotations: Collection<String>): String? {
        for (ann in annotations) {
            val match = annPattern.matchEntire(ann) ?: continue
            val annName = match.groupValues[1]
            if (annName == "RequestMapping" || annName.endsWith("Mapping")) {
                val content = match.groupValues.getOrNull(2) ?: continue
                val path = pathPattern.find(content)?.groupValues?.get(1)
                if (path != null) return normalizePath(path)
            }
        }
        return null
    }

    private fun extractMethodFromRequestMapping(ann: String): String {
        if (ann.contains("RequestMethod.")) {
            return """RequestMethod\.(\w+)""".toRegex().find(ann)?.groupValues?.get(1) ?: "GET"
        }
        return "GET"
    }

    private fun normalizePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return "/"
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }
}
