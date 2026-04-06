package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadataExtractor
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawAnnotation
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.springframework.stereotype.Component

/**
 * Извлекает метаданные из Feign-клиентов: @FeignClient(name, url, path)
 * и mapping-аннотаций на методах интерфейса.
 */
@Component
class FeignClientExtractor : ApiMetadataExtractor {
    override fun id() = "feign-client"
    override fun supports(lang: Lang) = (lang == Lang.kotlin || lang == Lang.java)

    private val mappingToMethod = mapOf(
        "GetMapping" to "GET",
        "PostMapping" to "POST",
        "PutMapping" to "PUT",
        "DeleteMapping" to "DELETE",
        "PatchMapping" to "PATCH",
        "RequestMapping" to "GET",
    )

    private val annPattern = """@?(?:[\w.]+\.)?(\w+)(?:\s*\((.*)\))?""".toRegex()
    private val pathPattern = """"([^"]+)"""".toRegex()

    override fun extractTypeMetadata(type: RawType, ctx: NodeKindContext): ApiMetadata? {
        // Structured first
        val feignAnn = type.annotations.find { it.name == "FeignClient" }
        if (feignAnn != null) {
            val name = feignAnn.getString("name") ?: feignAnn.getString("value") ?: feignAnn.value()
            val url = feignAnn.getString("url")
            val path = feignAnn.getString("path")
            return ApiMetadata.HttpEndpoint(
                method = "*",
                path = path?.let { normalizePath(it) } ?: "/",
                basePath = path?.let { normalizePath(it) },
                headers = buildMap {
                    if (name != null) put("feign.name", name)
                    if (url != null) put("feign.url", url)
                },
            )
        }

        // Regex fallback
        for (ann in type.annotationsRepr) {
            val match = annPattern.matchEntire(ann) ?: continue
            if (match.groupValues[1] != "FeignClient") continue
            val content = match.groupValues.getOrNull(2) ?: ""
            val name = extractNamedParam(content, "name", "value")
            val url = extractNamedParam(content, "url")
            val path = extractNamedParam(content, "path")
            return ApiMetadata.HttpEndpoint(
                method = "*",
                path = path?.let { normalizePath(it) } ?: "/",
                basePath = path?.let { normalizePath(it) },
                headers = buildMap {
                    if (name != null) put("feign.name", name)
                    if (url != null) put("feign.url", url)
                },
            )
        }

        return null
    }

    override fun extractFunctionMetadata(
        function: RawFunction,
        ownerType: RawType?,
        ctx: NodeKindContext,
    ): ApiMetadata? {
        // Only process methods of Feign interfaces
        val isFeignOwner = ownerType?.annotations?.any { it.name == "FeignClient" } == true
            || ownerType?.annotationsRepr?.any { it.contains("FeignClient") } == true
        if (!isFeignOwner) return null

        // Structured annotations first
        for (ann in function.annotations) {
            val method = mappingToMethod[ann.name] ?: continue
            val path = ann.getString("value")
                ?: ann.getString("path")
                ?: ann.getStringArray("value")?.firstOrNull()

            val finalMethod = if (ann.name == "RequestMapping") {
                val m = ann.getString("method") ?: continue
                m.substringAfterLast('.').uppercase()
            } else {
                method
            }

            return ApiMetadata.HttpEndpoint(
                method = finalMethod,
                path = normalizePath(path ?: "/"),
                basePath = null,
            )
        }

        // Regex fallback
        for (ann in function.annotationsRepr) {
            val match = annPattern.matchEntire(ann) ?: continue
            val annName = match.groupValues[1]
            val method = mappingToMethod[annName] ?: continue
            val content = match.groupValues.getOrNull(2) ?: ""
            val path = pathPattern.find(content)?.groupValues?.get(1)

            return ApiMetadata.HttpEndpoint(
                method = method,
                path = normalizePath(path ?: "/"),
                basePath = null,
            )
        }

        return null
    }

    private fun extractNamedParam(content: String, vararg names: String): String? {
        for (name in names) {
            val pattern = """$name\s*=\s*["']([^"']+)["']""".toRegex()
            val match = pattern.find(content)
            if (match != null) return match.groupValues[1]
        }
        // Try simple value
        val simplePattern = """^\s*["']([^"']+)["']""".toRegex()
        return simplePattern.find(content)?.groupValues?.get(1)
    }

    private fun normalizePath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return "/"
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }
}
