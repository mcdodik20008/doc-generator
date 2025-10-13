package com.bftcom.docgenerator.graph.impl

import com.bftcom.docgenerator.domain.enums.NodeKind
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

/**
 * # KotlinSourceWalker
 *
 * Лёгкий обходчик исходников Kotlin **без зависимости от IntelliJ VFS/Проекта**.
 *
 * ## Что делает
 * - Рекурсивно перечисляет файлы Kotlin под указанным корнем (через NIO `Files.walk`).
 * - Парсит каждый файл в PSI с помощью [KtPsiFactory] (только синтаксический парсинг).
 * - Извлекает:
 *   - пакет файла → [SourceVisitor.onPackage];
 *   - объявления типов (классы/интерфейсы/объекты) → [SourceVisitor.onType] c простыми именами суперклассов;
 *   - поля класса → [SourceVisitor.onField];
 *   - функции (членов и top-level) → [SourceVisitor.onFunction] c простым списком вызовов внутри тела.
 *
 * ## Что НЕ делает (осознанные ограничения)
 * - **Нет резолва символов / типов / FQN** — работает только на синтаксисе.
 * - **Нет учёта перегрузок/импортов/расширений** — `callsSimple` содержит то, что написано в исходнике.
 * - **Не различает `b.ping()` (вызов по переменной) и `B.ping()` (статический/квалифицированный)** —
 *   по умолчанию возвращается простое имя `ping`. См. TODO ниже как расширить.
 *
 * ## Фильтры/поддержка
 * - Игнорируются каталоги: `.git/`, `build/`, `out/`, `node_modules/`.
 * - Поддерживаются `.kt` и `.kts`.
 *
 * ## Сложность
 * - В худшем случае ~O(N + S), где N — число файлов, S — суммарная длина текстов (парсинг + линейный проход).
 *
 * ## Расширения (план)
 * - Для корректного `CALLS foo.A.makeAndUse -> foo.B.ping`:
 *   1) Отслеживать типы локальных переменных при присваивании `val b = B()` (минимальная эвристика).
 *   2) В `collectCallNames` распознавать `KtDotQualifiedExpression` и, если знаем тип `b`, добавлять токен вида `"B.ping"`.
 *   3) В визиторе `onFunction` уметь расширять `"B.ping"` → `"$currentPkg.B.ping"` (или иной FQN).
 */
@Component
class KotlinSourceWalker : SourceWalker {
    /**
     * Стек локальных областей видимости для функций.
     * Каждая карта содержит сопоставление `имяПеременной -> ПростоеИмяТипа`.
     *
     * **Важно:** в текущей реализации стек и методы ниже — заготовка под увеличение точности.
     * По умолчанию `collectCallNames` возвращает только простые имена, стек не используется.
     */
    private val locals = ArrayDeque<MutableMap<String, String>>() // стек {varName -> TypeSimple}
    /**
     * Рекурсивно обходит исходники под [root], парсит каждый Kotlin-файл и репортит структуру через [visitor].
     *
     * @param root Корень каталога исходников (должен существовать и быть директорией).
     * @param visitor Получатель событий о пакетах/типах/полях/функциях.
     * @throws IllegalArgumentException Если [root] не директория.
     */
    override fun walk(
        root: Path,
        visitor: SourceVisitor,
    ) {
        require(root.isDirectory()) { "Source root must be a directory: $root" }

        val disposable = Disposer.newDisposable()
        try {
            val cfg =
                CompilerConfiguration().apply {
                    put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
                    // addKotlinSourceRoot тут не нужен, мы читаем файлы сами
                }
            val env =
                KotlinCoreEnvironment.createForProduction(
                    disposable,
                    cfg,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES,
                )
            val project = env.project
            val psiFactory = KtPsiFactory(project, false)

            // 1) Список файлов
            val paths =
                Files.walk(root).use { stream ->
                    stream
                        .filter { Files.isRegularFile(it) }
                        .filter {
                            val s = it.toString()
                            s.endsWith(".kt") || s.endsWith(".kts")
                        }
                        // отсекаем мусорные папки по пути
                        .filter { p ->
                            val s = p.toString()
                            !s.contains("${File.separator}.git${File.separator}") &&
                                    !s.contains("${File.separator}build${File.separator}") &&
                                    !s.contains("${File.separator}out${File.separator}") &&
                                    !s.contains("${File.separator}node_modules${File.separator}")
                        }.toList()
                }

            // 2) Парсим и обрабатываем
            for (p in paths) {
                val text = Files.readString(p)
                val ktFile = psiFactory.createFile(p.fileName.toString(), text)

                val relPath =
                    try {
                        root.relativize(p).toString()
                    } catch (_: IllegalArgumentException) {
                        p.toString()
                    }
                val pkg = ktFile.packageFqName.asString()

                visitor.onPackage(pkg, relPath)
                processKtFile(ktFile, visitor, pkg, relPath) // твой существующий метод
            }
        } finally {
            Disposer.dispose(disposable)
        }
    }

    /**
     * Извлекает из [ktFile] типы, их поля и функции (включая top-level), и репортит это в [visitor].
     *
     * @param ktFile PSI-файл (уже разобран).
     * @param visitor Приёмник событий.
     * @param pkg FQN пакета файла (`""` если пакет не указан).
     * @param path Путь файла относительно [walk.root] (или абсолютный, если `relativize` не удался).
     */
    private fun processKtFile(
        ktFile: KtFile,
        visitor: SourceVisitor,
        pkg: String,
        path: String,
    ) {
        // types
        ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { decl ->
            val name = decl.name ?: return@forEach
            val kind =
                when (decl) {
                    is KtClass -> if (decl.isInterface()) NodeKind.INTERFACE else NodeKind.CLASS
                    is KtObjectDeclaration -> NodeKind.CLASS
                    else -> NodeKind.CLASS
                }
            val fqn =
                listOf(pkg.takeIf { it.isNotBlank() }, name)
                    .filterNotNull()
                    .joinToString(".")
            val supertypes =
                decl.superTypeListEntries
                    .mapNotNull { it.typeAsUserType?.referencedName }

            visitor.onType(kind, fqn, pkg, name, path, linesOf(ktFile, decl), supertypes)

            // fields
            decl.declarations.filterIsInstance<KtProperty>().forEach { prop ->
                visitor.onField(fqn, prop.name ?: return@forEach, path, linesOf(ktFile, prop))
            }

            // member functions
            decl.declarations.filterIsInstance<KtNamedFunction>().forEach { funDecl ->
                val calls = collectCallNames(funDecl)
                visitor.onFunction(
                    fqn,
                    funDecl.name ?: return@forEach,
                    funDecl.valueParameters.map { it.name ?: "_" },
                    path,
                    linesOf(ktFile, funDecl),
                    calls,
                )
            }
        }

        // top-level functions
        ktFile.declarations.filterIsInstance<KtNamedFunction>().forEach { funDecl ->
            val calls = collectCallNames(funDecl)
            visitor.onFunction(
                null,
                funDecl.name ?: return@forEach,
                funDecl.valueParameters.map { it.name ?: "_" },
                path,
                linesOf(ktFile, funDecl),
                calls,
            )
        }
    }

    /**
     * Возвращает 1-based диапазон строк `[start, end]` для [element] внутри текста [file].
     *
     * Алгоритм линейный по длине текста, без индексации — для редких вызовов в тестах/индексации подходит.
     */
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

    /**
     * Собирает **простые** имена вызываемых функций внутри тела [funDecl].
     *
     * Возвращает список токенов (в порядке обхода), например:
     * ```
     * fun bar() { baz(); qux(42) }  -> ["baz", "qux"]
     * ```
     *
     * ⚠️ Ограничения текущей версии:
     * - Не различает `b.ping()` и `B.ping()` — вернёт просто `"ping"`.
     * - Не добавляет квалификацию пакетом/типом.
     *
     * ## Как расширить до "B.ping"
     * - Обойти `KtDotQualifiedExpression` и вытянуть `receiver.callee` + `selector.callee`.
     * - Если `receiver` — имя переменной, заглянуть в `locals` (см. методы ниже) и подставить тип:
     *   вернуть токен `"B.ping"`.
     */
    private fun collectCallNames(funDecl: KtNamedFunction): List<String> {
        val calls = mutableListOf<String>()

        // локальная область видимости переменных для одной функции
        enterFunction()
        try {
            funDecl.bodyExpression?.accept(object : KtTreeVisitorVoid() {

                override fun visitProperty(prop: KtProperty) {
                    // Ищем простое присваивание конструктора: val b = B()
                    val varName = prop.name
                    val init = prop.initializer
                    if (varName != null && init is KtCallExpression) {
                        // callee: "B" или "foo.B"
                        val typeCandidate = init.calleeExpression?.text
                        if (!typeCandidate.isNullOrBlank()) {
                            val typeSimple = typeCandidate.substringAfterLast('.')
                            // очень грубая эвристика: класс с заглавной буквы
                            if (typeSimple.isNotEmpty() && typeSimple[0].isUpperCase()) {
                                onConstructorAssign(varName, typeSimple) // b -> B
                            }
                        }
                    }
                    super.visitProperty(prop)
                }

                override fun visitDotQualifiedExpression(expr: KtDotQualifiedExpression) {
                    // Ловим b.ping() -> "B.ping" (если знаем тип b)
                    val receiver = expr.receiverExpression
                    val selector = expr.selectorExpression
                    if (receiver is KtNameReferenceExpression && selector is KtCallExpression) {
                        val varName = receiver.getReferencedName()
                        val methodName = selector.calleeExpression?.text
                        if (!methodName.isNullOrBlank()) {
                            onMemberCall(varName, methodName, calls) // добавит "B.ping" или "ping"
                            // ВАЖНО: не даём этому же вызову повторно попасть в visitCallExpression
                            // (ниже просто не зовём super.visitDotQualifiedExpression, а обойдём детей вручную без callee)
                            selector.valueArgumentList?.accept(this)
                            selector.lambdaArguments.forEach { it.accept(this) }
                            return
                        }
                    }
                    super.visitDotQualifiedExpression(expr)
                }

                override fun visitCallExpression(expression: KtCallExpression) {
                    // Добавляем только "голые" вызовы: baz()
                    // Если этот Call — часть b.ping(), он будет обработан в visitDotQualifiedExpression
                    val parent = expression.parent
                    val isPartOfDot = parent is KtDotQualifiedExpression &&
                            (parent.selectorExpression === expression)

                    if (!isPartOfDot) {
                        expression.calleeExpression?.text?.let { name ->
                            if (name.isNotBlank()) calls += name
                        }
                    }
                    super.visitCallExpression(expression)
                }
            })
        } finally {
            exitFunction()
        }

        return calls
    }

    // ===== Ниже — заготовки под типизацию локальных переменных (не используются в текущей версии) =====

    /** Войти в тело функции: создаём новую карту локальных переменных. */
    private fun enterFunction() {
        locals.addLast(mutableMapOf())
    }

    /** Выйти из функции: удаляем текущую карту локальных переменных. */
    private fun exitFunction() {
        locals.removeLast()
    }

    /** Текущая область видимости локальных переменных. */
    private fun currentLocals(): MutableMap<String, String> = locals.last()

    /**
     * Зарегистрировать присваивание конструктора:
     * ```
     * val b = B()
     * ```
     * @param varName имя переменной (`"b"`)
     * @param typeSimple простое имя типа (`"B"`)
     */
    private fun onConstructorAssign(varName: String, typeSimple: String) {
        currentLocals()[varName] = typeSimple // пример: "b" -> "B"
    }

    /**
     * Зарегистрировать член-вызов:
     * ```
     * b.ping()
     * ```
     * Если тип `b` известен, добавляем квалифицированный токен `"B.ping"`, иначе — простое имя `"ping"`.
     */
    private fun onMemberCall(varName: String, methodName: String, calls: MutableList<String>) {
        val t = currentLocals()[varName]
        if (t != null) {
            calls += "$t.$methodName" // пример: "B.ping"
        } else {
            calls += methodName       // фоллбек: "ping"
        }
    }
}
