package com.bftcom.docgenerator.graph.impl.apimetadata.extractors

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadataExtractor
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawAnnotation
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

    // ===== Regex fallback (legacy) =====

    private val annPattern = """@?(?:[\w.]+\.)?(\w+)(?:\s*\((.*)\))?""".toRegex()
    private val pathPattern = """"([^"]+)"""".toRegex()

    override fun extractFunctionMetadata(
        function: RawFunction,
        ownerType: RawType?,
        ctx: NodeKindContext,
    ): ApiMetadata? {
        // Structured annotations first
        if (function.annotations.isNotEmpty()) {
            val result = extractFromStructured(function.annotations, ownerType)
            if (result != null) return result
        }
        // Fallback to regex-based extraction
        return extractFromAnnotationsRepr(function.annotationsRepr, ownerType)
    }

    override fun extractTypeMetadata(type: RawType, ctx: NodeKindContext): ApiMetadata? {
        // Structured first
        if (type.annotations.isNotEmpty()) {
            val path = extractPathFromStructured(type.annotations)
            if (path != null) {
                return ApiMetadata.HttpEndpoint(method = "*", path = path, basePath = path)
            }
        }
        // Fallback
        val path = extractPathFromRaw(type.annotationsRepr) ?: return null
        return ApiMetadata.HttpEndpoint(method = "*", path = path, basePath = path)
    }

    // ===== Structured annotation extraction =====

    private fun extractFromStructured(
        annotations: List<RawAnnotation>,
        ownerType: RawType?,
    ): ApiMetadata? {
        for (ann in annotations) {
            val method = mappingToMethod[ann.name] ?: continue

            // Extract path: try "value", then "path", then array forms
            val extractedPath = ann.getString("value")
                ?: ann.getString("path")
                ?: ann.getStringArray("value")?.firstOrNull()
                ?: ann.getStringArray("path")?.firstOrNull()

            // For @RequestMapping, resolve HTTP method from "method" parameter
            val finalMethod = if (ann.name == "RequestMapping") {
                resolveRequestMappingMethod(ann)
            } else {
                method
            }

            val basePath = ownerType?.let {
                extractPathFromStructured(it.annotations) ?: extractPathFromRaw(it.annotationsRepr)
            }

            return ApiMetadata.HttpEndpoint(
                method = finalMethod,
                path = normalizePath(extractedPath ?: "/"),
                basePath = basePath
            )
        }
        return null
    }

    private fun extractPathFromStructured(annotations: List<RawAnnotation>): String? {
        for (ann in annotations) {
            if (ann.name != "RequestMapping" && !ann.name.endsWith("Mapping")) continue
            val path = ann.getString("value")
                ?: ann.getString("path")
                ?: ann.getStringArray("value")?.firstOrNull()
                ?: ann.getStringArray("path")?.firstOrNull()
            if (path != null) return normalizePath(path)
        }
        return null
    }

    private fun resolveRequestMappingMethod(ann: RawAnnotation): String {
        val methodParam = ann.getString("method")
            ?: ann.getStringArray("method")?.firstOrNull()
            ?: return "GET"
        // Handle "RequestMethod.POST" -> "POST"
        return methodParam.substringAfterLast('.').uppercase()
    }

    // ===== Regex fallback =====

    private fun extractFromAnnotationsRepr(
        annotations: Set<String>,
        ownerType: RawType?,
    ): ApiMetadata? {
        for (ann in annotations) {
            val match = annPattern.matchEntire(ann) ?: continue
            val annName = match.groupValues[1]
            val method = mappingToMethod[annName] ?: continue

            val parenthesesContent = match.groupValues.getOrNull(2)
            val extractedPath = parenthesesContent?.let {
                pathPattern.find(it)?.groupValues?.get(1)
            }

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
