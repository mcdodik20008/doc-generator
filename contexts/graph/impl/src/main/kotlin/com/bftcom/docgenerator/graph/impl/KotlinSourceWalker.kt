package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.graph.api.KDocFetcher
import com.bftcom.docgenerator.graph.api.SourceVisitor
import com.bftcom.docgenerator.graph.api.SourceWalker
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.RawUsage
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
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
        classpath: List<File>, // <-- 2. ПРИНИМАЕМ CLASSPATH
    ) {
        log.info("Starting source walk at root [$root]...")
        require(root.isDirectory()) { "Source root must be a directory: $root" }

        val disposable = Disposer.newDisposable()
        try {
            // --- 3. "КОРМЁЖКА" (FEEDING) ---
            val cfg =
                CompilerConfiguration().apply {
                    put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)

                    if (classpath.isNotEmpty()) {
                        log.info("Feeding ${classpath.size} classpath roots to KotlinCoreEnvironment.")
                        addJvmClasspathRoots(classpath)
                    } else {
                        log.warn("!!! Classpath is empty. PSI parser will be incomplete.")
                        log.warn("!!! This may cause 'PSI body is NULL' warnings.")
                    }
                }

            val env =
                KotlinCoreEnvironment.createForProduction(disposable, cfg, EnvironmentConfigFiles.JVM_CONFIG_FILES)
            // --- КОНЕЦ КОРМЁЖКИ ---

            val project = env.project
            val psiFactory = KtPsiFactory(project, markGenerated = false)

            log.info("Scanning for Kotlin files...")
            val paths =
                Files.walk(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) }
                        .filter {
                            val s = it.toString()
                            s.endsWith(".kt") // || s.endsWith(".kts")
                        }.filter { p ->
                            val s = p.toString()
                            !s.contains("${File.separator}.git${File.separator}") &&
                                !s.contains("${File.separator}build${File.separator}") &&
                                !s.contains("${File.separator}out${File.separator}") &&
                                !s.contains("${File.separator}node_modules${File.separator}") &&
                                !s.contains("dependencies.kt")
                        }.toList()
                }
            log.info("Found ${paths.size} files to process.")

            val rich = (visitor as? SourceVisitor)

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

                processKtFile(ktFile, visitor, rich, pkg, relPath, classpath)
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
        rich: SourceVisitor?,
        pkg: String,
        path: String,
        classpath: List<File>,
    ) {
        // types
        ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { decl ->
            val name = decl.name ?: return@forEach
            val annotations = getAnnotationShortNames(decl)
            val kind =
                when {
                    name.lowercase().contains("test") -> NodeKind.TEST
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

                if (funDecl.bodyExpression == null) {
                    log.warn(
                        "!!! PSI body for [${funDecl.name}] in [$path] is NULL! Usages will be empty. (Classpath was fed: ${classpath.isNotEmpty()})",
                    )
                }

                val src = extractFunctionByIndent(ktFile, funDecl)
                val (usages, isBodyNull) =
                    if (funDecl.bodyExpression != null) {
                        // "Правильный" путь, если PSI сработал
                        Pair(collectRawUsagesFromPsi(funDecl), false)
                    } else {
                        // Наш "костыльный" путь, если PSI сдох
                        Pair(collectRawUsagesFromText(src), true)
                    }
                if (isBodyNull) {
                    log.warn(
                        "!!! PSI body for [${funDecl.name}] in [$path] is NULL! (Classpath was fed: ${classpath.isNotEmpty()}). Using TEXT PARSER for usages.",
                    )
                }

                // Собираем исключения (throw-выражения)
                val throwsTypes =
                    if (funDecl.bodyExpression != null) {
                        collectThrowsFromPsi(funDecl)
                    } else {
                        collectThrowsFromText(src)
                    }

                val fspan = linesOf(ktFile, funDecl)
                if (rich != null) {
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
                        throwsTypes,
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

            // --- ДОБАВЛЯЕМ ЛОГГИРОВАНИЕ ---
            if (funDecl.bodyExpression == null) {
                log.warn(
                    "!!! PSI body for [${funDecl.name}] in [$path] is NULL! Usages will be empty. (Classpath was fed: ${classpath.isNotEmpty()})",
                )
            }
            // --- КОНЕЦ ЛОГГИРОВАНИЯ ---
            val src = funDecl.text
            val (usages, isBodyNull) =
                if (funDecl.bodyExpression != null) {
                    // "Правильный" путь, если PSI сработал
                    Pair(collectRawUsagesFromPsi(funDecl), false)
                } else {
                    // Наш "костыльный" путь, если PSI сдох
                    Pair(collectRawUsagesFromText(src), true)
                }
            if (isBodyNull) {
                log.warn(
                    "!!! PSI body for [${funDecl.name}] in [$path] is NULL! (Classpath was fed: ${classpath.isNotEmpty()}). Using TEXT PARSER for usages.",
                )
            }

            // Собираем исключения (throw-выражения)
            val throwsTypes =
                if (funDecl.bodyExpression != null) {
                    collectThrowsFromPsi(funDecl)
                } else {
                    collectThrowsFromText(src)
                }
            val span = linesOf(ktFile, funDecl)
            if (rich != null) {
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
                    throwsTypes,
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

    // --- (Здесь все твои утилиты: extractFunctionByIndent, linesOf, collectRawUsages и т.д.) ---
    // ...
    // ... (Я не буду их перепечатывать, они остаются как есть) ...
    // ...

    // --- УТИЛИТЫ: (я скопирую их из нашей истории, чтобы файл был полным) ---

    // Возвращает полный исходник функции по индентации:
    // 1) если PSI дал тело — используем его;
    // 2) иначе ищем "}" на строке с тем же отступом, что и строка с "fun";
    private fun extractFunctionByIndent(
        ktFile: KtFile,
        funDecl: KtNamedFunction,
    ): String {
        // 1) нормальный путь через PSI
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

        // 2) фолбэк по индентации
        val text = ktFile.text
        val headerStart = funDecl.textRange.startOffset

        // строка, где начинается "fun"
        val funLineStart = text.lastIndexOf('\n', startIndex = headerStart).let { if (it == -1) 0 else it + 1 }
        val funLineEnd = text.indexOf('\n', startIndex = funLineStart).let { if (it == -1) text.length else it }

        val funIndent = leadingIndent(text, funLineStart, funLineEnd) // отступ строки с fun
        val afterParams =
            (
                funDecl.valueParameterList?.textRange?.endOffset
                    ?: funDecl.textRange.endOffset
            ).coerceIn(0, text.length)

        // пропустим пробелы/комменты от afterParams до тела
        var scan = skipWsAndComments(text, afterParams)

        // expression-body: "=" раньше "{"
        val eqPos = text.indexOf('=', startIndex = scan)
        val bracePos = text.indexOf('{', startIndex = scan)
        if (eqPos != -1 && (bracePos == -1 || eqPos < bracePos)) {
            // берём до конца строки / до ';'
            var end = text.indexOf('\n', startIndex = eqPos)
            if (end == -1) end = text.length
            val semi = text.indexOf(';', startIndex = eqPos)
            if (semi != -1 && semi < end) end = semi + 1
            return text.substring(headerStart, end)
        }

        // блочное тело: ищем строку, где первый не-пробел — '}', и её отступ == отступу fun
        var i = if (bracePos != -1) bracePos else scan
        if (i < 0) i = scan
        // начать поиск с начала следующей строки, чтобы точно попасть на линии тела
        i = text.indexOf('\n', startIndex = i).let { if (it == -1) scan else it + 1 }

        while (i < text.length) {
            val lineEnd = text.indexOf('\n', startIndex = i).let { if (it == -1) text.length else it }
            // отступ текущей строки
            val lineIndent = leadingIndent(text, i, lineEnd)
            val firstNonWs = firstNonWhitespace(text, i, lineEnd)

            if (firstNonWs != -1 && text[firstNonWs] == '}' && lineIndent == funIndent) {
                // нашли закрывающую скобку функции на той же колонке, что и "fun"
                val end = (firstNonWs + 1).coerceAtMost(text.length)
                return text.substring(headerStart, end)
            }
            i = lineEnd + 1
        }

        // если не нашли — хотя бы вернём шапку функции
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
                        if (n == '/') { // // коммент до конца строки
                            i += 2
                            while (i < text.length && text[i] != '\n') i++
                        } else if (n == '*') { // /* ... */
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

    /**
     *
     * Костыль 2.0: Сбор "сырых" использований через Regex (Фолбэк).
     */
    private fun collectRawUsagesFromText(sourceCode: String): List<RawUsage> {
        val usages = mutableSetOf<RawUsage>() // Используем Set, чтобы избежать дублей

        // 1. Простые вызовы: calcA(...)
        // Ищет "слово" (буквы/цифры/подчеркивание), после которого идет "("
        val simpleCallRegex = """\b(\w+)\s*\(""".toRegex()
        simpleCallRegex.findAll(sourceCode).forEach { match ->
            val name = match.groupValues[1]
            if (name.isNotBlank() && !NOISE.contains(name)) {
                usages.add(RawUsage.Simple(name, isCall = true))
            }
        }

        // 2. Вызовы через точку: dependenciesHolder.businessCalendarService.getWorkingDaysBetween(...)
        // Ищет "что-то.слово("
        // '([\w.]+\b)' - захватывает 'a.b.c'
        // '\.(\w+)\s*\(' - захватывает '.method('
        val dotCallRegex = """([\w.]+\b)\.(\w+)\s*\(""".toRegex()
        dotCallRegex.findAll(sourceCode).forEach { match ->
            val receiver = match.groupValues[1]
            val member = match.groupValues[2]

            // Отфильтровываем шум (e.g., "log.info", "map.forEach")
            val simpleReceiver = receiver.substringAfterLast('.')

            if (receiver.isNotBlank() && member.isNotBlank() &&
                !NOISE.contains(member) && !NOISE.contains(simpleReceiver)
            ) {
                usages.add(RawUsage.Dot(receiver, member, isCall = true))
            }
        }

        return usages.toList()
    }

    /**
     * Сбор типов исключений из throw-выражений через PSI.
     */
    private fun collectThrowsFromPsi(funDecl: KtNamedFunction): List<String> {
        val throwsTypes = mutableSetOf<String>()
        funDecl.bodyExpression?.accept(
            object : KtTreeVisitorVoid() {
                override fun visitThrowExpression(expression: KtThrowExpression) {
                    // Получаем тип исключения из throw-выражения
                    val thrownExpression = expression.thrownExpression
                    when (thrownExpression) {
                        is KtNameReferenceExpression -> {
                            // Простое имя: throw IllegalArgumentException()
                            thrownExpression.getReferencedName().let { throwsTypes.add(it) }
                        }
                        is KtCallExpression -> {
                            // Вызов конструктора: throw IllegalArgumentException("message")
                            thrownExpression.calleeExpression?.text?.let { throwsTypes.add(it) }
                        }
                        is KtDotQualifiedExpression -> {
                            // Квалифицированное имя: throw com.example.CustomException()
                            val receiver = thrownExpression.receiverExpression.text
                            val selector = thrownExpression.selectorExpression
                            val typeName = when (selector) {
                                is KtNameReferenceExpression -> selector.getReferencedName()
                                is KtCallExpression -> selector.calleeExpression?.text
                                else -> null
                            }
                            if (receiver.isNotBlank() && typeName != null) {
                                throwsTypes.add("$receiver.$typeName")
                            } else if (typeName != null) {
                                throwsTypes.add(typeName)
                            }
                        }
                        else -> {
                            // Пытаемся извлечь тип из текста
                            thrownExpression?.text?.let { text ->
                                // Упрощённый парсинг: ищем имя типа
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

    /**
     * Сбор типов исключений из throw-выражений через Regex (fallback).
     */
    private fun collectThrowsFromText(sourceCode: String): List<String> {
        val throwsTypes = mutableSetOf<String>()

        // Паттерн для throw-выражений: throw Type(...) или throw Type
        // Ищем "throw" за которым следует имя типа (начинается с заглавной буквы)
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
