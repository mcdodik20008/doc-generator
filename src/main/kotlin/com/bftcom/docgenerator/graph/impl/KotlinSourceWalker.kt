package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.RichSourceVisitor
import com.bftcom.docgenerator.graph.api.SourceVisitor
import com.bftcom.docgenerator.graph.api.SourceWalker
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

@Component
class KotlinSourceWalker : SourceWalker {

    override fun walk(root: Path, visitor: SourceVisitor) {
        require(root.isDirectory()) { "Source root must be a directory: $root" }

        val disposable = Disposer.newDisposable()
        try {
            val cfg = CompilerConfiguration().apply {
                put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
            }
            val env = KotlinCoreEnvironment.createForProduction(disposable, cfg, EnvironmentConfigFiles.JVM_CONFIG_FILES)
            val project = env.project
            val psiFactory = KtPsiFactory(project, markGenerated = false)

            val paths = Files.walk(root).use { stream ->
                stream.filter { Files.isRegularFile(it) }
                    .filter { val s = it.toString(); s.endsWith(".kt") || s.endsWith(".kts") }
                    .filter { p ->
                        val s = p.toString()
                        !s.contains("${File.separator}.git${File.separator}") &&
                                !s.contains("${File.separator}build${File.separator}") &&
                                !s.contains("${File.separator}out${File.separator}") &&
                                !s.contains("${File.separator}node_modules${File.separator}")
                    }
                    .toList()
            }

            val rich = (visitor as? RichSourceVisitor)

            for (p in paths) {
                val text = Files.readString(p)
                val ktFile = psiFactory.createFile(p.fileName.toString(), text)

                val relPath = try { root.relativize(p).toString() } catch (_: IllegalArgumentException) { p.toString() }
                val pkg = ktFile.packageFqName.asString()

                visitor.onPackage(pkg, relPath)
                processKtFile(ktFile, visitor, rich, pkg, relPath)
            }
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private fun processKtFile(
        ktFile: KtFile,
        visitor: SourceVisitor,
        rich: RichSourceVisitor?,
        pkg: String,
        path: String,
    ) {
        // types
        ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { decl ->
            val name = decl.name ?: return@forEach
            val kind = when (decl) {
                is KtClass -> if (decl.isInterface()) NodeKind.INTERFACE else NodeKind.CLASS
                is KtObjectDeclaration -> NodeKind.CLASS
                else -> NodeKind.CLASS
            }
            val fqn = listOfNotNull(pkg.takeIf { it.isNotBlank() }, name).joinToString(".")
            val supertypes = decl.superTypeListEntries.mapNotNull { it.typeAsUserType?.referencedName }
            val span = linesOf(ktFile, decl)

            if (rich != null) {
                val src = decl.text
                val sig = signatureFromDeclText(decl.text)
                val doc = (decl as? KtDeclaration)?.docComment?.text
                rich.onTypeEx(kind, fqn, pkg, name, path, span, supertypes, src, sig, doc)
            } else {
                visitor.onType(kind, fqn, pkg, name, path, span, supertypes)
            }

            // fields
            decl.declarations.filterIsInstance<KtProperty>().forEach { prop ->
                val pspan = linesOf(ktFile, prop)
                if (rich != null) {
                    val src = prop.text
                    val doc = prop.docComment?.text
                    rich.onFieldEx(fqn, prop.name ?: return@forEach, path, pspan, src, doc)
                } else {
                    visitor.onField(fqn, prop.name ?: return@forEach, path, pspan)
                }
            }

            // member functions
            decl.declarations.filterIsInstance<KtNamedFunction>().forEach { funDecl ->
                val calls = collectCallNames(funDecl)
                val fspan = linesOf(ktFile, funDecl)
                if (rich != null) {
                    val src = funDecl.text
                    val sig = signatureFromFunction(funDecl)
                    val doc = funDecl.docComment?.text
                    rich.onFunctionEx(fqn, funDecl.name ?: return@forEach,
                        funDecl.valueParameters.map { it.name ?: "_" },
                        path, fspan, calls, src, sig, doc)
                } else {
                    visitor.onFunction(fqn, funDecl.name ?: return@forEach,
                        funDecl.valueParameters.map { it.name ?: "_" },
                        path, fspan, calls)
                }
            }
        }

        // top-level functions
        ktFile.declarations.filterIsInstance<KtNamedFunction>().forEach { funDecl ->
            val calls = collectCallNames(funDecl)
            val span = linesOf(ktFile, funDecl)
            if (rich != null) {
                val src = funDecl.text
                val sig = signatureFromFunction(funDecl)
                val doc = funDecl.docComment?.text
                rich.onFunctionEx(null, funDecl.name ?: return@forEach,
                    funDecl.valueParameters.map { it.name ?: "_" },
                    path, span, calls, src, sig, doc)
            } else {
                visitor.onFunction(null, funDecl.name ?: return@forEach,
                    funDecl.valueParameters.map { it.name ?: "_" },
                    path, span, calls)
            }
        }
    }

    /** 1-based диапазон строк элемента в файле. */
    private fun linesOf(file: KtFile, element: KtElement): IntRange {
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
        return s.lineSequence().firstOrNull()?.trim()?.ifBlank { null }
    }

    /** Точная сигнатура функции: до тела/стрелки/=. */
    private fun signatureFromFunction(f: KtNamedFunction): String? {
        val start = f.textRange.startOffset
        val bodyStart = when {
            f.equalsToken != null -> f.equalsToken!!.textRange.startOffset
            f.bodyExpression != null -> f.bodyExpression!!.textRange.startOffset
            else -> f.textRange.endOffset
        }
        val full = f.containingKtFile.text
        val raw = full.substring(start, bodyStart)
        return raw.trim().removeSuffix("{").trimEnd().ifBlank { null }
    }

    /** Сбор простых имён вызовов + эвристика для b.ping() -> "B.ping", если ранее увидели val b = B(). */
    private fun collectCallNames(funDecl: KtNamedFunction): List<String> {
        val calls = mutableListOf<String>()
        val locals = ArrayDeque<MutableMap<String, String>>() // varName -> TypeSimple

        fun enter() = locals.addLast(mutableMapOf())
        fun exit() = locals.removeLast()
        fun curr() = locals.last()

        enter()
        try {
            funDecl.bodyExpression?.accept(object : KtTreeVisitorVoid() {

                override fun visitProperty(prop: KtProperty) {
                    val varName = prop.name
                    val init = prop.initializer
                    if (varName != null && init is KtCallExpression) {
                        val typeCandidate = init.calleeExpression?.text
                        if (!typeCandidate.isNullOrBlank()) {
                            val typeSimple = typeCandidate.substringAfterLast('.')
                            if (typeSimple.isNotEmpty() && typeSimple[0].isUpperCase()) {
                                curr()[varName] = typeSimple // b -> B
                            }
                        }
                    }
                    super.visitProperty(prop)
                }

                override fun visitDotQualifiedExpression(expr: KtDotQualifiedExpression) {
                    val receiver = expr.receiverExpression
                    val selector = expr.selectorExpression
                    if (receiver is KtNameReferenceExpression && selector is KtCallExpression) {
                        val varName = receiver.getReferencedName()
                        val methodName = selector.calleeExpression?.text
                        if (!methodName.isNullOrBlank()) {
                            val t = curr()[varName]
                            calls += if (t != null) "$t.$methodName" else methodName
                            selector.valueArgumentList?.accept(this)
                            selector.lambdaArguments.forEach { it.accept(this) }
                            return
                        }
                    }
                    super.visitDotQualifiedExpression(expr)
                }

                override fun visitCallExpression(expression: KtCallExpression) {
                    val parent = expression.parent
                    val isPartOfDot = parent is KtDotQualifiedExpression && (parent.selectorExpression === expression)
                    if (!isPartOfDot) {
                        expression.calleeExpression?.text?.let { name ->
                            if (name.isNotBlank()) calls += name
                        }
                    }
                    super.visitCallExpression(expression)
                }
            })
        } finally {
            exit()
        }

        return calls
    }
}
