package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.graph.api.model.RawAttrKey
import com.bftcom.docgenerator.graph.api.model.rawdecl.LineSpan
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.node.SourceVisitor
import com.bftcom.docgenerator.graph.api.node.SourceWalker
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.SchemaLoader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Парсер .proto файлов через Wire Schema.
 * Создаёт RawType для message/enum/service и RawFunction для rpc-методов.
 */
@Component
class ProtoSourceWalker : SourceWalker {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun walk(root: Path, visitor: SourceVisitor, classpath: List<File>) {
        log.info("Starting Proto source walk at root [$root]...")
        require(root.isDirectory()) { "Source root must be a directory: $root" }

        val protoPaths = Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().endsWith(".proto") }
                .filter { p ->
                    val s = p.toString()
                    !s.contains("${File.separator}.git${File.separator}") &&
                        !s.contains("${File.separator}build${File.separator}") &&
                        !s.contains("${File.separator}out${File.separator}") &&
                        !s.contains("${File.separator}target${File.separator}") &&
                        !s.contains("${File.separator}generated${File.separator}")
                }.toList()
        }

        if (protoPaths.isEmpty()) {
            log.info("No .proto files found.")
            return
        }
        log.info("Found ${protoPaths.size} .proto files to process.")

        // Parse each proto file individually using wire-schema
        protoPaths.forEachIndexed { index, protoPath ->
            val relPath = try {
                root.relativize(protoPath).toString()
            } catch (e: IllegalArgumentException) {
                protoPath.toString()
            }

            try {
                parseProtoFile(protoPath, root, relPath, visitor)
            } catch (e: Exception) {
                log.warn("Failed to parse proto file [{}]: {}", relPath, e.message)
            }

            if ((index + 1) % 20 == 0) {
                log.info("[${index + 1}/${protoPaths.size}] proto files processed")
            }
        }

        log.info("Finished processing ${protoPaths.size} proto files.")
    }

    private fun parseProtoFile(protoPath: Path, root: Path, relPath: String, visitor: SourceVisitor) {
        val content = Files.readString(protoPath)
        val lines = content.lines()

        // Extract package from proto file directly
        val pkgFqn = extractPackage(lines)
        val imports = extractImports(lines)

        visitor.onDecl(
            RawFileUnit(
                lang = SrcLang.proto,
                filePath = relPath,
                pkgFqn = pkgFqn,
                imports = imports,
                span = null,
                text = null,
                attributes = emptyMap(),
            )
        )

        // Try wire-schema parsing for structured data
        try {
            val schemaLoader = SchemaLoader(fileSystem = okio.FileSystem.SYSTEM)
            schemaLoader.initRoots(
                sourcePath = listOf(Location.get(root.toString())),
                protoPath = listOf(Location.get(root.toString())),
            )
            val schema = schemaLoader.loadSchema()

            val protoFile = schema.protoFiles.find { it.location.path == relPath }
            if (protoFile != null) {
                processProtoFileSchema(protoFile, visitor, pkgFqn, relPath, lines)
                return
            }
        } catch (e: Exception) {
            log.debug("Wire schema parsing failed for [{}], falling back to regex: {}", relPath, e.message)
        }

        // Fallback: regex-based parsing
        parseProtoFileRegex(lines, visitor, pkgFqn, relPath)
    }

    private fun processProtoFileSchema(
        protoFile: com.squareup.wire.schema.ProtoFile,
        visitor: SourceVisitor,
        pkgFqn: String?,
        path: String,
        lines: List<String>,
    ) {
        // Process messages and enums
        for (type in protoFile.types) {
            val simpleName = type.type.simpleName
            val fqn = listOfNotNull(pkgFqn, simpleName).joinToString(".")
            val kindRepr = when (type) {
                is com.squareup.wire.schema.EnumType -> "enum"
                is com.squareup.wire.schema.MessageType -> "message"
                else -> "message"
            }

            val span = findSpanForName(lines, simpleName)
            val doc = type.documentation

            visitor.onDecl(
                RawType(
                    lang = SrcLang.proto,
                    filePath = path,
                    pkgFqn = pkgFqn,
                    simpleName = simpleName,
                    kindRepr = kindRepr,
                    supertypesRepr = emptyList(),
                    annotationsRepr = emptyList(),
                    annotations = emptyList(),
                    span = span,
                    text = null,
                    attributes = buildMap {
                        put(RawAttrKey.FQN.key, fqn)
                        if (doc.isNotBlank()) put(RawAttrKey.KDOC_TEXT.key, doc)
                    },
                )
            )

            // Process nested types recursively
            if (type is com.squareup.wire.schema.MessageType) {
                for (nested in type.nestedTypes) {
                    val nestedName = nested.type.simpleName
                    val nestedFqn = "$fqn.$nestedName"
                    val nestedKind = when (nested) {
                        is com.squareup.wire.schema.EnumType -> "enum"
                        else -> "message"
                    }
                    visitor.onDecl(
                        RawType(
                            lang = SrcLang.proto,
                            filePath = path,
                            pkgFqn = pkgFqn,
                            simpleName = nestedName,
                            kindRepr = nestedKind,
                            supertypesRepr = emptyList(),
                            annotationsRepr = emptyList(),
                            annotations = emptyList(),
                            span = findSpanForName(lines, nestedName),
                            text = null,
                            attributes = buildMap {
                                put(RawAttrKey.FQN.key, nestedFqn)
                                val nestedDoc = nested.documentation
                                if (nestedDoc.isNotBlank()) put(RawAttrKey.KDOC_TEXT.key, nestedDoc)
                            },
                        )
                    )
                }
            }
        }

        // Process services
        for (service in protoFile.services) {
            val serviceName = service.type.simpleName
            val serviceFqn = listOfNotNull(pkgFqn, serviceName).joinToString(".")
            val serviceDoc = service.documentation

            visitor.onDecl(
                RawType(
                    lang = SrcLang.proto,
                    filePath = path,
                    pkgFqn = pkgFqn,
                    simpleName = serviceName,
                    kindRepr = "service",
                    supertypesRepr = emptyList(),
                    annotationsRepr = emptyList(),
                    annotations = emptyList(),
                    span = findSpanForName(lines, serviceName),
                    text = null,
                    attributes = buildMap {
                        put(RawAttrKey.FQN.key, serviceFqn)
                        if (serviceDoc.isNotBlank()) put(RawAttrKey.KDOC_TEXT.key, serviceDoc)
                    },
                )
            )

            // Process RPCs as functions
            for (rpc in service.rpcs) {
                val rpcName = rpc.name
                val requestType = rpc.requestType?.simpleName ?: "Unknown"
                val responseType = rpc.responseType?.simpleName ?: "Unknown"
                val rpcDoc = rpc.documentation

                val signature = buildString {
                    append("rpc $rpcName(")
                    if (rpc.requestStreaming) append("stream ")
                    append(requestType)
                    append(") returns (")
                    if (rpc.responseStreaming) append("stream ")
                    append(responseType)
                    append(")")
                }

                visitor.onDecl(
                    RawFunction(
                        lang = SrcLang.proto,
                        filePath = path,
                        pkgFqn = pkgFqn,
                        ownerFqn = serviceFqn,
                        name = rpcName,
                        signatureRepr = signature,
                        paramNames = listOf(requestType),
                        paramTypeNames = listOf(requestType),
                        annotationsRepr = emptySet(),
                        annotations = emptyList(),
                        rawUsages = emptyList(),
                        throwsRepr = emptyList(),
                        kdoc = rpcDoc.takeIf { it.isNotBlank() },
                        span = findSpanForName(lines, rpcName),
                        text = null,
                        attributes = buildMap {
                            put(RawAttrKey.FQN.key, "$serviceFqn.$rpcName")
                            put("requestType", rpc.requestType?.toString() ?: requestType)
                            put("responseType", rpc.responseType?.toString() ?: responseType)
                            if (rpc.requestStreaming) put("requestStreaming", "true")
                            if (rpc.responseStreaming) put("responseStreaming", "true")
                        },
                    )
                )
            }
        }
    }

    /**
     * Regex-based fallback parsing when wire-schema fails.
     */
    private fun parseProtoFileRegex(
        lines: List<String>,
        visitor: SourceVisitor,
        pkgFqn: String?,
        path: String,
    ) {
        val servicePattern = Regex("""^\s*service\s+(\w+)\s*\{""")
        val rpcPattern = Regex("""^\s*rpc\s+(\w+)\s*\(\s*(stream\s+)?(\w[\w.]*)\s*\)\s*returns\s*\(\s*(stream\s+)?(\w[\w.]*)\s*\)""")
        val messagePattern = Regex("""^\s*message\s+(\w+)\s*\{""")
        val enumPattern = Regex("""^\s*enum\s+(\w+)\s*\{""")

        var currentService: String? = null
        var currentServiceFqn: String? = null

        lines.forEachIndexed { lineIdx, line ->
            val lineNo = lineIdx + 1

            // Message
            messagePattern.find(line)?.let { match ->
                val name = match.groupValues[1]
                val fqn = listOfNotNull(pkgFqn, name).joinToString(".")
                visitor.onDecl(
                    RawType(
                        lang = SrcLang.proto,
                        filePath = path,
                        pkgFqn = pkgFqn,
                        simpleName = name,
                        kindRepr = "message",
                        supertypesRepr = emptyList(),
                        annotationsRepr = emptyList(),
                        annotations = emptyList(),
                        span = LineSpan(lineNo, lineNo),
                        text = null,
                        attributes = mapOf(RawAttrKey.FQN.key to fqn),
                    )
                )
            }

            // Enum
            enumPattern.find(line)?.let { match ->
                val name = match.groupValues[1]
                val fqn = listOfNotNull(pkgFqn, name).joinToString(".")
                visitor.onDecl(
                    RawType(
                        lang = SrcLang.proto,
                        filePath = path,
                        pkgFqn = pkgFqn,
                        simpleName = name,
                        kindRepr = "enum",
                        supertypesRepr = emptyList(),
                        annotationsRepr = emptyList(),
                        annotations = emptyList(),
                        span = LineSpan(lineNo, lineNo),
                        text = null,
                        attributes = mapOf(RawAttrKey.FQN.key to fqn),
                    )
                )
            }

            // Service
            servicePattern.find(line)?.let { match ->
                val name = match.groupValues[1]
                currentService = name
                currentServiceFqn = listOfNotNull(pkgFqn, name).joinToString(".")
                visitor.onDecl(
                    RawType(
                        lang = SrcLang.proto,
                        filePath = path,
                        pkgFqn = pkgFqn,
                        simpleName = name,
                        kindRepr = "service",
                        supertypesRepr = emptyList(),
                        annotationsRepr = emptyList(),
                        annotations = emptyList(),
                        span = LineSpan(lineNo, lineNo),
                        text = null,
                        attributes = mapOf(RawAttrKey.FQN.key to currentServiceFqn!!),
                    )
                )
            }

            // RPC
            rpcPattern.find(line)?.let { match ->
                val rpcName = match.groupValues[1]
                val reqStreaming = match.groupValues[2].isNotBlank()
                val reqType = match.groupValues[3]
                val respStreaming = match.groupValues[4].isNotBlank()
                val respType = match.groupValues[5]
                val ownerFqn = currentServiceFqn ?: listOfNotNull(pkgFqn, "UnknownService").joinToString(".")

                val signature = buildString {
                    append("rpc $rpcName(")
                    if (reqStreaming) append("stream ")
                    append(reqType)
                    append(") returns (")
                    if (respStreaming) append("stream ")
                    append(respType)
                    append(")")
                }

                visitor.onDecl(
                    RawFunction(
                        lang = SrcLang.proto,
                        filePath = path,
                        pkgFqn = pkgFqn,
                        ownerFqn = ownerFqn,
                        name = rpcName,
                        signatureRepr = signature,
                        paramNames = listOf(reqType),
                        paramTypeNames = listOf(reqType),
                        annotationsRepr = emptySet(),
                        annotations = emptyList(),
                        rawUsages = emptyList(),
                        throwsRepr = emptyList(),
                        kdoc = null,
                        span = LineSpan(lineNo, lineNo),
                        text = null,
                        attributes = buildMap {
                            put(RawAttrKey.FQN.key, "$ownerFqn.$rpcName")
                            put("requestType", reqType)
                            put("responseType", respType)
                            if (reqStreaming) put("requestStreaming", "true")
                            if (respStreaming) put("responseStreaming", "true")
                        },
                    )
                )
            }
        }
    }

    private fun extractPackage(lines: List<String>): String? {
        val packagePattern = Regex("""^\s*package\s+([\w.]+)\s*;""")
        return lines.firstNotNullOfOrNull { packagePattern.find(it)?.groupValues?.get(1) }
    }

    private fun extractImports(lines: List<String>): List<String> {
        val importPattern = Regex("""^\s*import\s+"([^"]+)"\s*;""")
        return lines.mapNotNull { importPattern.find(it)?.groupValues?.get(1) }
    }

    private fun findSpanForName(lines: List<String>, name: String): LineSpan? {
        val idx = lines.indexOfFirst { it.contains(name) }
        return if (idx >= 0) LineSpan(idx + 1, idx + 1) else null
    }
}
