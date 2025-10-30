package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.RichSourceVisitor
import com.bftcom.docgenerator.graph.api.SourceVisitor
import com.bftcom.docgenerator.graph.api.SourceWalker
import com.bftcom.docgenerator.graph.model.KDocParsed
import com.bftcom.docgenerator.graph.model.RawUsage
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

@Component
class KotlinSourceWalker(
    val kDocFetcher: KDocFetcher,
) : SourceWalker {
    companion object {
        private val log = LoggerFactory.getLogger(KotlinSourceWalker::class.java)
        private val NOISE =
            setOf("listOf", "map", "of", "timer", "start", "stop", "sequenceOf", "arrayOf", "mutableListOf")
    }

    override fun walk(
        root: Path,
        visitor: SourceVisitor,
    ) {
        log.info("Starting source walk at root [$root]...")
        require(root.isDirectory()) { "Source root must be a directory: $root" }

        val disposable = Disposer.newDisposable()
        try {
            val cfg =
                CompilerConfiguration().apply {
                    put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                }
            val env =
                KotlinCoreEnvironment.createForProduction(disposable, cfg, EnvironmentConfigFiles.JVM_CONFIG_FILES)
            val project = env.project
            val psiFactory = KtPsiFactory(project, markGenerated = false)

            log.info("Scanning for Kotlin files...")
            val paths =
                Files.walk(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) }
                        .filter {
                            val s = it.toString()
                            s.endsWith(".kt") || s.endsWith(".kts")
                        }.filter { p ->
                            val s = p.toString()
                            !s.contains("${File.separator}.git${File.separator}") &&
                                !s.contains("${File.separator}build${File.separator}") &&
                                !s.contains("${File.separator}out${File.separator}") &&
                                !s.contains("${File.separator}node_modules${File.separator}")
                        }.toList()
                }
            log.info("Found ${paths.size} files to process.")

            val rich = (visitor as? RichSourceVisitor)

            val totalFiles = paths.size
            log.info("Processing $totalFiles files...")

            paths.forEachIndexed { index, p ->
                val relPath =
                    try {
                        root.relativize(p).toString()
                    } catch (_: IllegalArgumentException) {
                        p.toString()
                    }
                val percent = if (totalFiles > 0) ((index + 1) * 100.0 / totalFiles).toInt() else 100
                log.info("[${index + 1}/$totalFiles, $percent%] Processing: $relPath")

                val text = Files.readString(p)
                val ktFile = psiFactory.createFile(p.fileName.toString(), text)
                val pkg = ktFile.packageFqName.asString()
                val imports: List<String> = ktFile.importDirectives.mapNotNull { it.importPath?.pathStr }

                visitor.onPackage(pkg, relPath)
                // передадим контекст файла с импортами (расширение интерфейса RichSourceVisitor)
                rich?.onFileContext(pkg, relPath, imports)

                processKtFile(ktFile, visitor, rich, pkg, relPath, imports)
            }

            log.info("Finished processing ${paths.size} files.")
        } finally {
            Disposer.dispose(disposable)
            log.info("Source walk completed.")
        }
    }

    private fun processKtFile(
        ktFile: KtFile,
        visitor: SourceVisitor,
        rich: RichSourceVisitor?,
        pkg: String,
        path: String,
        imports: List<String>,
    ) {
        // types
        ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { decl ->
            val name = decl.name ?: return@forEach
            val annotations = getAnnotationShortNames(decl)
            val kind =
                when {
                    annotations.contains("Controller") || name.contains("Controller") -> NodeKind.ENDPOINT
                    annotations.contains("Configuration") || name.contains("Configuration") -> NodeKind.CONFIG
                    annotations.contains("Service") || name.contains("Service") -> NodeKind.SERVICE
                    annotations.contains("Mapper") || name.contains("Mapper") -> NodeKind.MAPPER
                    decl is KtObjectDeclaration -> NodeKind.CLASS
                    decl is KtClass ->
                        when {
                            decl.isEnum() -> NodeKind.ENUM
                            decl.isInterface() -> NodeKind.INTERFACE
                            decl.isData() -> NodeKind.RECORD
                            else -> NodeKind.CLASS
                        }

                    else -> NodeKind.CLASS
                }
            val fqn = listOfNotNull(pkg.takeIf { it.isNotBlank() }, name).joinToString(".")
            val supertypes = decl.superTypeListEntries.mapNotNull { it.typeAsUserType?.referencedName }
            val span = linesOf(ktFile, decl)

            if (rich != null) {
                val src = decl.text
                val sig = signatureFromDeclText(decl.text)
                val doc = kDocFetcher.parseKDoc(decl)?.let { kDocFetcher.toDocString(it) }
                val kdocParsed = kDocFetcher.parseKDoc(decl)
                rich.onTypeEx(
                    kind,
                    fqn,
                    pkg,
                    name,
                    path,
                    span,
                    supertypes,
                    src,
                    sig,
                    doc,
                    kDocFetcher.toMeta(kdocParsed),
                )
            } else {
                visitor.onType(kind, fqn, pkg, name, path, span, supertypes)
            }

            // fields
            decl.declarations.filterIsInstance<KtProperty>().forEach { prop ->
                val pspan = linesOf(ktFile, prop)
                if (rich != null) {
                    val src = prop.text
                    val doc = kDocFetcher.parseKDoc(decl)?.let { kDocFetcher.toDocString(it) }
                    val kdocParsed = kDocFetcher.parseKDoc(prop)
                    rich.onFieldEx(
                        fqn,
                        prop.name ?: return@forEach,
                        path,
                        pspan,
                        src,
                        doc,
                        kDocFetcher.toMeta(kdocParsed),
                    )
                } else {
                    visitor.onField(fqn, prop.name ?: return@forEach, path, pspan)
                }
            }

            // member functions
            decl.declarations.filterIsInstance<KtNamedFunction>().forEach { funDecl ->
                val usages = collectRawUsages(funDecl)
                val fspan = linesOf(ktFile, funDecl)
                if (rich != null) {
                    val src = funDecl.text
                    val sig = signatureFromFunction(funDecl)
                    val doc = kDocFetcher.parseKDoc(decl)?.let { kDocFetcher.toDocString(it) }
                    val annotationsFun = getAnnotationShortNames(funDecl)
                    val kdocParsed = kDocFetcher.parseKDoc(funDecl)
                    rich.onFunctionEx(
                        fqn,
                        funDecl.name ?: return@forEach,
                        funDecl.valueParameters.map { it.name ?: "_" },
                        path,
                        fspan,
                        usages,
                        src,
                        sig,
                        doc,
                        annotationsFun,
                        kDocFetcher.toMeta(kdocParsed),
                    )
                } else {
                    visitor.onFunction(
                        fqn,
                        funDecl.name ?: return@forEach,
                        funDecl.valueParameters.map { it.name ?: "_" },
                        path,
                        fspan,
                        usages,
                    )
                }
            }
        }

        // top-level functions
        ktFile.declarations.filterIsInstance<KtNamedFunction>().forEach { funDecl ->
            val usages = collectRawUsages(funDecl)
            val span = linesOf(ktFile, funDecl)
            if (rich != null) {
                val src = funDecl.text
                val sig = signatureFromFunction(funDecl)
                val doc = kDocFetcher.parseKDoc(funDecl)?.let { kDocFetcher.toDocString(it) }
                val annotations = getAnnotationShortNames(funDecl)
                val kdocParsed = kDocFetcher.parseKDoc(funDecl)
                rich.onFunctionEx(
                    null,
                    funDecl.name ?: return@forEach,
                    funDecl.valueParameters.map { it.name ?: "_" },
                    path,
                    span,
                    usages,
                    src,
                    sig,
                    doc,
                    annotations,
                    kDocFetcher.toMeta(kdocParsed),
                )
            } else {
                visitor.onFunction(
                    null,
                    funDecl.name ?: return@forEach,
                    funDecl.valueParameters.map { it.name ?: "_" },
                    path,
                    span,
                    usages,
                )
            }
        }
    }

    /** 1-based диапазон строк элемента в файле. */
    private fun linesOf(
        file: KtFile,
        element: KtElement,
    ): IntRange {
        val text = file.text

        fun toLine(offset: Int): Int {
            var line = 1
            var i = 0
            while (i < offset && i < text.length) if (text[i++] == '\n') line++
            return line
        }

        val start = toLine(element.textRange.startOffset)
        val end = toLine(element.textRange.endOffset)
        return start..end
    }

    /** Универсальная сигнатура «до { или =» для классов/объектов/свойств. */
    private fun signatureFromDeclText(text: String): String? {
        val idx = text.indexOfAny(charArrayOf('{', '='))
        val s = if (idx >= 0) text.take(idx) else text
        return s
            .lineSequence()
            .firstOrNull()
            ?.trim()
            ?.ifBlank { null }
    }

    /** Точная сигнатура функции: до тела/стрелки/=. */
    private fun signatureFromFunction(f: KtNamedFunction): String? {
        val start = f.textRange.startOffset
        val bodyStart =
            when {
                f.equalsToken != null -> f.equalsToken!!.textRange.startOffset
                f.bodyExpression != null -> f.bodyExpression!!.textRange.startOffset
                else -> f.textRange.endOffset
            }
        val full = f.containingKtFile.text
        val raw = full.substring(start, bodyStart)
        return raw
            .trim()
            .removeSuffix("{")
            .trimEnd()
            .ifBlank { null }
    }

    private fun getAnnotationShortNames(decl: KtDeclaration): Set<String> =
        decl.annotationEntries.mapNotNull { it.shortName?.asString() }.toSet()

    /**
     * Фаза 1: сбор "сырых" использований, с поддержкой safe-call, простых конструкторов и ссылок.
     */
    private fun collectRawUsages(funDecl: KtNamedFunction): List<RawUsage> {
        val usages = mutableListOf<RawUsage>()
        funDecl.bodyExpression?.accept(
            object : KtTreeVisitorVoid() {
                override fun visitDotQualifiedExpression(expr: KtDotQualifiedExpression) {
                    val receiver =
                        expr.receiverExpression.text
                            .replace("?.", ".")
                            .replace("::", ".")
                    val selector = expr.selectorExpression
                    val isCall = selector is KtCallExpression
                    val memberName =
                        when (selector) {
                            is KtCallExpression -> selector.calleeExpression?.text
                            is KtNameReferenceExpression -> selector.getReferencedName()
                            else -> null
                        }

                    if (receiver.isNotBlank() && !memberName.isNullOrBlank()) {
                        if (!(NOISE.contains(receiver) || NOISE.contains(memberName))) {
                            usages.add(RawUsage.Dot(receiver, memberName, isCall))
                        }
                    }
                    super.visitDotQualifiedExpression(expr)
                }

                override fun visitCallExpression(expression: KtCallExpression) {
                    val parent = expression.parent
                    // простые вызовы, не часть a.b()
                    val isPartOfDot = parent is KtDotQualifiedExpression && (parent.selectorExpression === expression)
                    if (!isPartOfDot) {
                        expression.calleeExpression?.text?.let { name ->
                            if (name.isNotBlank() && !NOISE.contains(name)) {
                                usages.add(RawUsage.Simple(name, isCall = true)) // в т.ч. конструкторы Type(...)
                            }
                        }
                    }
                    super.visitCallExpression(expression)
                }
            },
        )
        return usages
    }

    private fun KDocParsed.toMeta(): Map<String, Any?> =
        mapOf(
            "summary" to summary,
            "description" to description,
            "params" to params,
            "properties" to properties,
            "returns" to returns,
            "throws" to throws,
            "seeAlso" to seeAlso,
            "since" to since,
            "otherTags" to otherTags,
        )
}
