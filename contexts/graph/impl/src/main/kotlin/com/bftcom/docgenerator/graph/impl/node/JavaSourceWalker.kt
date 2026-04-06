package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.shared.node.RawUsage
import com.bftcom.docgenerator.graph.api.model.RawAttrKey
import com.bftcom.docgenerator.graph.api.model.rawdecl.LineSpan
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawAnnotation
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.node.SourceVisitor
import com.bftcom.docgenerator.graph.api.node.SourceWalker
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.MemberValuePair
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Парсер Java-исходников через JavaParser.
 * Создаёт RawType, RawFunction, RawField с lang=java.
 */
@Component
class JavaSourceWalker : SourceWalker {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun walk(root: Path, visitor: SourceVisitor, classpath: List<File>) {
        log.info("Starting Java source walk at root [$root]...")
        require(root.isDirectory()) { "Source root must be a directory: $root" }

        val paths = Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { it.toString().endsWith(".java") }
                .filter { p ->
                    val s = p.toString()
                    !s.contains("${File.separator}.git${File.separator}") &&
                        !s.contains("${File.separator}build${File.separator}") &&
                        !s.contains("${File.separator}out${File.separator}") &&
                        !s.contains("${File.separator}target${File.separator}") &&
                        !s.contains("${File.separator}generated${File.separator}")
                }.toList()
        }

        if (paths.isEmpty()) {
            log.info("No Java files found.")
            return
        }
        log.info("Found ${paths.size} Java files to process.")

        paths.forEachIndexed { index, p ->
            val relPath = try {
                root.relativize(p).toString()
            } catch (e: IllegalArgumentException) {
                p.toString()
            }

            try {
                val cu = StaticJavaParser.parse(p)
                processCompilationUnit(cu, visitor, relPath)
            } catch (e: Exception) {
                log.warn("Failed to parse Java file [{}]: {}", relPath, e.message)
            }

            if ((index + 1) % 50 == 0) {
                log.info("[${index + 1}/${paths.size}] Java files processed")
            }
        }

        log.info("Finished processing ${paths.size} Java files.")
    }

    private fun processCompilationUnit(cu: CompilationUnit, visitor: SourceVisitor, path: String) {
        val pkgFqn = cu.packageDeclaration.map { it.nameAsString }.orElse(null)
        val imports = cu.imports.map { it.nameAsString }

        visitor.onDecl(
            RawFileUnit(
                lang = SrcLang.java,
                filePath = path,
                pkgFqn = pkgFqn,
                imports = imports,
                span = null,
                text = null,
                attributes = emptyMap(),
            )
        )

        cu.types.forEach { typeDecl ->
            processType(typeDecl, visitor, pkgFqn, path)
        }
    }

    private fun processType(typeDecl: TypeDeclaration<*>, visitor: SourceVisitor, pkgFqn: String?, path: String) {
        val name = typeDecl.nameAsString
        val fqn = listOfNotNull(pkgFqn, name).joinToString(".")

        val kindRepr = when (typeDecl) {
            is EnumDeclaration -> "enum"
            is ClassOrInterfaceDeclaration -> when {
                typeDecl.isInterface -> "interface"
                else -> "class"
            }
            else -> "class"
        }

        val supertypesRepr = if (typeDecl is ClassOrInterfaceDeclaration) {
            typeDecl.extendedTypes.map { it.nameAsString } +
                typeDecl.implementedTypes.map { it.nameAsString }
        } else {
            emptyList()
        }

        val annotationsRepr = typeDecl.annotations.map { it.nameAsString }
        val structuredAnnotations = typeDecl.annotations.map { extractAnnotation(it) }
        val span = typeDecl.range.map { LineSpan(it.begin.line, it.end.line) }.orElse(null)
        val sourceText = typeDecl.toString()
        val signature = sourceText.lineSequence().firstOrNull()?.trim()

        val javadoc = typeDecl.javadocComment.map { it.content.trim() }.orElse(null)

        visitor.onDecl(
            RawType(
                lang = SrcLang.java,
                filePath = path,
                pkgFqn = pkgFqn,
                simpleName = name,
                kindRepr = kindRepr,
                supertypesRepr = supertypesRepr,
                annotationsRepr = annotationsRepr,
                annotations = structuredAnnotations,
                span = span,
                text = sourceText,
                attributes = mapOf(
                    RawAttrKey.FQN.key to fqn,
                    RawAttrKey.SIGNATURE.key to signature,
                    RawAttrKey.KDOC_TEXT.key to javadoc,
                ),
            )
        )

        // Fields
        typeDecl.fields.forEach { fieldDecl ->
            processField(fieldDecl, visitor, fqn, pkgFqn, path)
        }

        // Methods
        typeDecl.methods.forEach { methodDecl ->
            processMethod(methodDecl, visitor, fqn, pkgFqn, path)
        }

        // Nested types
        typeDecl.members.filterIsInstance<TypeDeclaration<*>>().forEach { nested ->
            processType(nested, visitor, pkgFqn, path)
        }
    }

    private fun processField(
        fieldDecl: FieldDeclaration,
        visitor: SourceVisitor,
        ownerFqn: String,
        pkgFqn: String?,
        path: String,
    ) {
        fieldDecl.variables.forEach { variable ->
            val span = variable.range.map { LineSpan(it.begin.line, it.end.line) }.orElse(null)
            val annotationsRepr = fieldDecl.annotations.map { it.nameAsString }
            val structuredAnnotations = fieldDecl.annotations.map { extractAnnotation(it) }
            val javadoc = fieldDecl.javadocComment.map { it.content.trim() }.orElse(null)

            visitor.onDecl(
                RawField(
                    lang = SrcLang.java,
                    filePath = path,
                    pkgFqn = pkgFqn,
                    ownerFqn = ownerFqn,
                    name = variable.nameAsString,
                    typeRepr = fieldDecl.elementType.asString(),
                    annotationsRepr = annotationsRepr,
                    annotations = structuredAnnotations,
                    kdoc = javadoc,
                    span = span,
                    text = fieldDecl.toString(),
                    attributes = emptyMap(),
                )
            )
        }
    }

    private fun processMethod(
        methodDecl: MethodDeclaration,
        visitor: SourceVisitor,
        ownerFqn: String,
        pkgFqn: String?,
        path: String,
    ) {
        val name = methodDecl.nameAsString
        val paramNames = methodDecl.parameters.map { it.nameAsString }
        val paramTypeNames = methodDecl.parameters.map { it.typeAsString.substringBefore('<').substringAfterLast('.') }
        val annotationsRepr = methodDecl.annotations.map { it.nameAsString }.toSet()
        val structuredAnnotations = methodDecl.annotations.map { extractAnnotation(it) }

        val rawUsages = collectRawUsages(methodDecl)
        val throwsRepr = methodDecl.thrownExceptions.map { it.asString() }

        val span = methodDecl.range.map { LineSpan(it.begin.line, it.end.line) }.orElse(null)
        val signature = methodDecl.declarationAsString
        val javadoc = methodDecl.javadocComment.map { it.content.trim() }.orElse(null)

        visitor.onDecl(
            RawFunction(
                lang = SrcLang.java,
                filePath = path,
                pkgFqn = pkgFqn,
                ownerFqn = ownerFqn,
                name = name,
                signatureRepr = signature,
                paramNames = paramNames,
                paramTypeNames = paramTypeNames,
                annotationsRepr = annotationsRepr,
                annotations = structuredAnnotations,
                rawUsages = rawUsages,
                throwsRepr = throwsRepr,
                kdoc = javadoc,
                span = span,
                text = methodDecl.toString(),
                attributes = emptyMap(),
            )
        )
    }

    private fun collectRawUsages(methodDecl: MethodDeclaration): List<RawUsage> {
        val usages = mutableListOf<RawUsage>()
        methodDecl.findAll(MethodCallExpr::class.java).forEach { call ->
            val scope = call.scope.map { it.toString() }.orElse(null)
            val methodName = call.nameAsString
            if (scope != null) {
                usages.add(RawUsage.Dot(scope, methodName, isCall = true))
            } else {
                usages.add(RawUsage.Simple(methodName, isCall = true))
            }
        }
        return usages
    }

    private fun extractAnnotation(ann: AnnotationExpr): RawAnnotation {
        val name = ann.nameAsString
        val params = mutableMapOf<String, Any>()

        when (ann) {
            is SingleMemberAnnotationExpr -> {
                params["value"] = ann.memberValue.toString().removeSurrounding("\"")
            }
            is NormalAnnotationExpr -> {
                ann.pairs.forEach { pair: MemberValuePair ->
                    params[pair.nameAsString] = pair.value.toString().removeSurrounding("\"")
                }
            }
        }

        return RawAnnotation(name = name, params = params)
    }
}
