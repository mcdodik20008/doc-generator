package com.bftcom.docgenerator.graph.impl.node

import com.bftcom.docgenerator.domain.node.RawUsage
import com.bftcom.docgenerator.graph.api.model.RawAttrKey
import com.bftcom.docgenerator.graph.api.model.rawdecl.LineSpan
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.model.rawdecl.SrcLang
import com.bftcom.docgenerator.graph.api.node.KDocFetcher
import com.bftcom.docgenerator.graph.api.node.SourceVisitor
import com.bftcom.docgenerator.graph.api.node.SourceWalker
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtThrowExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

@Component
class KotlinSourceWalker(
    private val kDocFetcher: KDocFetcher,
) : SourceWalker {
    companion object {
        private val log = LoggerFactory.getLogger(KotlinSourceWalker::class.java)

        private val NOISE =
            setOf(
                "listOf",
                "map",
                "of",
                "timer",
                "start",
                "stop",
                "sequenceOf",
                "arrayOf",
                "mutableListOf",
            )
    }

    override fun walk(
        root: Path,
        visitor: SourceVisitor,
        classpath: List<File>,
    ) {
        log.info("Starting source walk at root [$root]...")
        require(root.isDirectory()) { "Source root must be a directory: $root" }

        val disposable = Disposer.newDisposable()
        try {
            val cfg =
                CompilerConfiguration().apply {
                    put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                    if (classpath.isNotEmpty()) {
                        log.info("Feeding ${classpath.size} classpath roots to KotlinCoreEnvironment.")
                        addJvmClasspathRoots(classpath)
                    } else {
                        log.warn("Classpath is empty. PSI parser may be incomplete (body==NULL warnings possible).")
                    }
                }

            val env =
                KotlinCoreEnvironment.createForProduction(
                    disposable,
                    cfg,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES,
                )
            val project = env.project
            val psiFactory = KtPsiFactory(project, markGenerated = false)

            log.info("Scanning for Kotlin files...")
            val paths =
                Files.walk(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) }
                        .filter { it.toString().endsWith(".kt") } // .kts не трогаем
                        .filter { p ->
                            val s = p.toString()
                            !s.contains("${File.separator}.git${File.separator}") &&
                                !s.contains("${File.separator}build${File.separator}") &&
                                !s.contains("${File.separator}out${File.separator}") &&
                                !s.contains("${File.separator}node_modules${File.separator}") &&
                                !s.contains("dependencies.kt")
                        }.toList()
                }
            log.info("Found ${paths.size} files to process.")

            val totalFiles = paths.size
            paths.forEachIndexed { index, p ->
                val relPath =
                    try {
                        root.relativize(p).toString()
                    } catch (e: IllegalArgumentException) {
                        log.warn("Failed to relativize path: root={}, path={}, error={}", root, p, e.message)
                        p.toString()
                    }
                val percent = if (totalFiles > 0) ((index + 1) * 100.0 / totalFiles).toInt() else 100
                log.info("[${index + 1}/$totalFiles, $percent%] Processing: $relPath")

                val text = Files.readString(p)
                val ktFile = psiFactory.createFile(p.fileName.toString(), text)
                val pkgFqn = ktFile.packageFqName.asString().ifBlank { null }
                val imports: List<String> = ktFile.importDirectives.mapNotNull { it.importPath?.pathStr }

                // Контекст файла
                visitor.onDecl(
                    RawFileUnit(
                        lang = SrcLang.kotlin,
                        filePath = relPath,
                        pkgFqn = pkgFqn,
                        imports = imports,
                        span = null,
                        text = null,
                        attributes = emptyMap(),
                    ),
                )

                processKtFile(ktFile, visitor, pkgFqn, relPath, classpath)
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
        pkgFqn: String?,
        path: String,
        classpath: List<File>,
    ) {
        // Типы
        ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { decl ->
            val name = decl.name ?: return@forEach

            val kindRepr =
                when (decl) {
                    is KtObjectDeclaration -> "object"
                    is KtClass ->
                        when {
                            decl.isEnum() -> "enum"
                            decl.isInterface() -> "interface"
                            decl.isData() -> "record"
                            else -> "class"
                        }
                    else -> "class"
                }

            val fqn = listOfNotNull(pkgFqn, name).joinToString(".")
            val supertypesRepr = decl.superTypeListEntries.mapNotNull { it.typeReference?.text ?: it.typeAsUserType?.text }
            val span = linesOf(ktFile, decl)?.let { LineSpan(it.first, it.last) }

            val sourceText = decl.text
            val signature = signatureFromDeclText(sourceText)
            val kdocParsed = kDocFetcher.parseKDoc(decl)
            val kdocText = kdocParsed?.let { kDocFetcher.toDocString(it) }
            val kdocMeta = kDocFetcher.toMeta(kdocParsed)
            val annotations = getAnnotationShortNames(decl).toList()

            visitor.onDecl(
                RawType(
                    lang = SrcLang.kotlin,
                    filePath = path,
                    pkgFqn = pkgFqn,
                    simpleName = name,
                    kindRepr = kindRepr,
                    supertypesRepr = supertypesRepr,
                    annotationsRepr = annotations,
                    span = span,
                    text = sourceText,
                    attributes =
                        mapOf(
                            RawAttrKey.FQN.key to fqn,
                            RawAttrKey.SIGNATURE.key to signature,
                            RawAttrKey.KDOC_TEXT.key to kdocText,
                            RawAttrKey.KDOC_META.key to kdocMeta,
                        ),
                ),
            )

            // Поля
            decl.declarations.filterIsInstance<KtProperty>().forEach { prop ->
                val pspan = linesOf(ktFile, prop)?.let { LineSpan(it.first, it.last) }
                val ptext = prop.text
                val pkdoc = kDocFetcher.parseKDoc(prop)?.let { kDocFetcher.toDocString(it) }
                val annotationsField = getAnnotationShortNames(prop).toList()

                visitor.onDecl(
                    RawField(
                        lang = SrcLang.kotlin,
                        filePath = path,
                        pkgFqn = pkgFqn,
                        ownerFqn = fqn,
                        name = prop.name ?: return@forEach,
                        typeRepr = prop.typeReference?.text,
                        annotationsRepr = annotationsField,
                        kdoc = pkdoc,
                        span = pspan,
                        text = ptext,
                        attributes = emptyMap(),
                    ),
                )
            }

            // Методы
            decl.declarations.filterIsInstance<KtNamedFunction>().forEach { funDecl ->
                if (funDecl.bodyExpression == null) {
                    log.warn("PSI body for [${funDecl.name}] in [$path] is NULL (classpath fed: ${classpath.isNotEmpty()})")
                }

                val fsrc = extractFunctionByIndent(ktFile, funDecl)
                val (rawUsages, bodyMissing) =
                    if (funDecl.bodyExpression != null) {
                        collectRawUsagesFromPsi(funDecl) to false
                    } else {
                        collectRawUsagesFromText(fsrc) to true
                    }
                if (bodyMissing) {
                    log.warn("Using TEXT parser for usages in [${funDecl.name}] at [$path]")
                }

                val throwsRepr =
                    if (funDecl.bodyExpression != null) {
                        collectThrowsFromPsi(funDecl)
                    } else {
                        collectThrowsFromText(fsrc)
                    }

                val fspan = linesOf(ktFile, funDecl)?.let { LineSpan(it.first, it.last) }
                val fsig = signatureFromFunction(funDecl)
                val fkdoc = kDocFetcher.parseKDoc(funDecl)?.let { kDocFetcher.toDocString(it) }
                val annotationsFun = getAnnotationShortNames(funDecl)

                visitor.onDecl(
                    RawFunction(
                        lang = SrcLang.kotlin,
                        filePath = path,
                        pkgFqn = pkgFqn,
                        ownerFqn = fqn,
                        name = funDecl.name ?: return@forEach,
                        signatureRepr = fsig,
                        paramNames = funDecl.valueParameters.map { it.name ?: "_" },
                        annotationsRepr = annotationsFun,
                        rawUsages = rawUsages,
                        throwsRepr = throwsRepr,
                        kdoc = fkdoc,
                        span = fspan,
                        text = fsrc,
                        attributes = emptyMap(),
                    ),
                )
            }
        }

        // Топ-левел функции
        ktFile.declarations.filterIsInstance<KtNamedFunction>().forEach { funDecl ->
            if (funDecl.bodyExpression == null) {
                log.warn("PSI body for top-level [${funDecl.name}] in [$path] is NULL (classpath fed: ${classpath.isNotEmpty()})")
            }

            val src = funDecl.text
            val (rawUsages, bodyMissing) =
                if (funDecl.bodyExpression != null) {
                    collectRawUsagesFromPsi(funDecl) to false
                } else {
                    collectRawUsagesFromText(src) to true
                }
            if (bodyMissing) {
                log.warn("Using TEXT parser for usages in top-level [${funDecl.name}] at [$path]")
            }

            val throwsRepr =
                if (funDecl.bodyExpression != null) {
                    collectThrowsFromPsi(funDecl)
                } else {
                    collectThrowsFromText(src)
                }

            val span = linesOf(ktFile, funDecl)?.let { LineSpan(it.first, it.last) }
            val sig = signatureFromFunction(funDecl)
            val kdoc = kDocFetcher.parseKDoc(funDecl)?.let { kDocFetcher.toDocString(it) }
            val annotations = getAnnotationShortNames(funDecl)

            visitor.onDecl(
                RawFunction(
                    lang = SrcLang.kotlin,
                    filePath = path,
                    pkgFqn = pkgFqn,
                    ownerFqn = null,
                    name = funDecl.name ?: return@forEach,
                    signatureRepr = sig,
                    paramNames = funDecl.valueParameters.map { it.name ?: "_" },
                    annotationsRepr = annotations,
                    rawUsages = rawUsages,
                    throwsRepr = throwsRepr,
                    kdoc = kdoc,
                    span = span,
                    text = src,
                    attributes = emptyMap(),
                ),
            )
        }
    }

    // ===== Утилиты =====

    private fun extractFunctionByIndent(
        ktFile: KtFile,
        funDecl: KtNamedFunction,
    ): String {
        funDecl.bodyBlockExpression?.let { body ->
            val start = funDecl.textRange.startOffset
            val end = body.textRange.endOffset
            return ktFile.text.substring(start, end)
        }
        funDecl.bodyExpression?.takeIf { it !is KtBlockExpression }?.let { exprBody ->
            val start = funDecl.textRange.startOffset
            val end = exprBody.textRange.endOffset
            return ktFile.text.substring(start, end)
        }

        val text = ktFile.text
        val headerStart = funDecl.textRange.startOffset
        val funLineStart = text.lastIndexOf('\n', startIndex = headerStart).let { if (it == -1) 0 else it + 1 }
        val funLineEnd = text.indexOf('\n', startIndex = funLineStart).let { if (it == -1) text.length else it }
        val funIndent = leadingIndent(text, funLineStart, funLineEnd)
        val afterParams =
            (funDecl.valueParameterList?.textRange?.endOffset ?: funDecl.textRange.endOffset).coerceIn(0, text.length)

        val scan = skipWsAndComments(text, afterParams)
        val eqPos = text.indexOf('=', startIndex = scan)
        val bracePos = text.indexOf('{', startIndex = scan)
        if (eqPos != -1 && (bracePos == -1 || eqPos < bracePos)) {
            var end = text.indexOf('\n', startIndex = eqPos)
            if (end == -1) end = text.length
            val semi = text.indexOf(';', startIndex = eqPos)
            if (semi != -1 && semi < end) end = semi + 1
            return text.substring(headerStart, end)
        }

        var i = if (bracePos != -1) bracePos else scan
        if (i < 0) i = scan
        i = text.indexOf('\n', startIndex = i).let { if (it == -1) scan else it + 1 }

        while (i < text.length) {
            val lineEnd = text.indexOf('\n', startIndex = i).let { if (it == -1) text.length else it }
            val lineIndent = leadingIndent(text, i, lineEnd)
            val firstNonWs = firstNonWhitespace(text, i, lineEnd)

            if (firstNonWs != -1 && text[firstNonWs] == '}' && lineIndent == funIndent) {
                val end = (firstNonWs + 1).coerceAtMost(text.length)
                return text.substring(headerStart, end)
            }
            i = lineEnd + 1
        }
        return text.substring(headerStart, funLineEnd)
    }

    private fun leadingIndent(
        text: String,
        lineStart: Int,
        lineEnd: Int,
    ): String {
        var p = lineStart
        while (p < lineEnd && (text[p] == ' ' || text[p] == '\t')) p++
        return text.substring(lineStart, p)
    }

    private fun firstNonWhitespace(
        text: String,
        lineStart: Int,
        lineEnd: Int,
    ): Int {
        var p = lineStart
        while (p < lineEnd) {
            val c = text[p]
            if (c != ' ' && c != '\t' && c != '\r') return p
            p++
        }
        return -1
    }

    private fun skipWsAndComments(
        text: String,
        start: Int,
    ): Int {
        var i = start
        while (i < text.length) {
            when (text[i]) {
                ' ', '\t', '\r', '\n' -> i++
                '/' ->
                    if (i + 1 < text.length) {
                        val n = text[i + 1]
                        if (n == '/') {
                            i += 2
                            while (i < text.length && text[i] != '\n') i++
                        } else if (n == '*') {
                            i += 2
                            while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
                            i = (i + 2).coerceAtMost(text.length)
                        } else {
                            return i
                        }
                    } else {
                        return i
                    }
                else -> return i
            }
        }
        return i
    }

    /** Возвращает 1-based диапазон строк элемента (или null, если текст пуст). */
    private fun linesOf(
        file: KtFile,
        element: KtElement,
    ): IntRange? {
        val text = file.text
        if (text.isEmpty()) return null

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

    /** Сбор сырых использований через PSI. */
    private fun collectRawUsagesFromPsi(funDecl: KtNamedFunction): List<RawUsage> {
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
                    val isPartOfDot = parent is KtDotQualifiedExpression && (parent.selectorExpression === expression)
                    if (!isPartOfDot) {
                        expression.calleeExpression?.text?.let { name ->
                            if (name.isNotBlank() && !NOISE.contains(name)) {
                                usages.add(RawUsage.Simple(name, isCall = true))
                            }
                        }
                    }
                    super.visitCallExpression(expression)
                }
            },
        )
        return usages
    }

    /** Фолбэк: сбор сырых использований по тексту. */
    private fun collectRawUsagesFromText(sourceCode: String): List<RawUsage> {
        val usages = mutableSetOf<RawUsage>()

        val simpleCallRegex = """\b(\w+)\s*\(""".toRegex()
        simpleCallRegex.findAll(sourceCode).forEach { match ->
            val name = match.groupValues[1]
            if (name.isNotBlank() && !NOISE.contains(name)) {
                usages.add(RawUsage.Simple(name, isCall = true))
            }
        }

        val dotCallRegex = """([\w.]+\b)\.(\w+)\s*\(""".toRegex()
        dotCallRegex.findAll(sourceCode).forEach { match ->
            val receiver = match.groupValues[1]
            val member = match.groupValues[2]
            val simpleReceiver = receiver.substringAfterLast('.')
            if (
                receiver.isNotBlank() && member.isNotBlank() &&
                !NOISE.contains(member) && !NOISE.contains(simpleReceiver)
            ) {
                usages.add(RawUsage.Dot(receiver, member, isCall = true))
            }
        }

        return usages.toList()
    }

    /** Сбор типов исключений через PSI. */
    private fun collectThrowsFromPsi(funDecl: KtNamedFunction): List<String> {
        val throwsTypes = mutableSetOf<String>()
        funDecl.bodyExpression?.accept(
            object : KtTreeVisitorVoid() {
                override fun visitThrowExpression(expression: KtThrowExpression) {
                    when (val thrown = expression.thrownExpression) {
                        is KtNameReferenceExpression ->
                            throwsTypes.add(thrown.getReferencedName())
                        is KtCallExpression ->
                            thrown.calleeExpression?.text?.let { throwsTypes.add(it) }
                        is KtDotQualifiedExpression -> {
                            val receiver = thrown.receiverExpression.text
                            val selector = thrown.selectorExpression
                            val typeName =
                                when (selector) {
                                    is KtNameReferenceExpression -> selector.getReferencedName()
                                    is KtCallExpression -> selector.calleeExpression?.text
                                    else -> null
                                }
                            if (!receiver.isNullOrBlank() && typeName != null) {
                                throwsTypes.add("$receiver.$typeName")
                            } else if (typeName != null) {
                                throwsTypes.add(typeName)
                            }
                        }
                        else -> {
                            thrown?.text?.let { text ->
                                val typeMatch = """\b([A-Z][A-Za-z0-9_]*)\b""".toRegex().find(text)
                                typeMatch?.groupValues?.get(1)?.let { throwsTypes.add(it) }
                            }
                        }
                    }
                    super.visitThrowExpression(expression)
                }
            },
        )
        return throwsTypes.toList()
    }

    /** Фолбэк: сбор типов исключений по тексту. */
    private fun collectThrowsFromText(sourceCode: String): List<String> {
        val throwsTypes = mutableSetOf<String>()
        val throwRegex = """\bthrow\s+([A-Z][A-Za-z0-9_]*(?:\.[A-Z][A-Za-z0-9_]*)*)""".toRegex()
        throwRegex.findAll(sourceCode).forEach { match ->
            val typeName = match.groupValues[1]
            if (typeName.isNotBlank()) {
                throwsTypes.add(typeName)
            }
        }
        return throwsTypes.toList()
    }
}
