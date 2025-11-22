# Алгоритм построения узлов графа кода

## Обзор

Данный документ описывает процесс построения узлов графа кода из исходников, полученных из Git-репозитория. Процесс включает парсинг исходного кода, извлечение деклараций и создание структурированных узлов в базе данных.

## Инструменты

- **Kotlin PSI** (`org.jetbrains.kotlin:kotlin-compiler-embeddable`) - парсинг Kotlin кода
- **IntelliJ Platform** - инфраструктура для PSI деревьев
- **Gradle Classpath** - зависимости проекта для разрешения типов

## Основной процесс

### Фаза 1: Инициализация Kotlin PSI парсера

**Сервис**: `KotlinSourceWalker.walk()`

#### Шаг 1.1: Создание KotlinCoreEnvironment

```kotlin
val cfg = CompilerConfiguration().apply {
    put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
    if (classpath.isNotEmpty()) {
        addJvmClasspathRoots(classpath)  // Добавление зависимостей
    }
}

val env = KotlinCoreEnvironment.createForProduction(
    disposable,
    cfg,
    EnvironmentConfigFiles.JVM_CONFIG_FILES
)
```

- Создается конфигурация компилятора
- Добавляется classpath (список JAR-файлов зависимостей)
- Создается окружение для production парсинга

**Важно**: Без classpath PSI парсер не может разрешить типы, и `bodyExpression` будет `NULL`.

#### Шаг 1.2: Создание PSI фабрики

```kotlin
val project = env.project
val psiFactory = KtPsiFactory(project, markGenerated = false)
```

Фабрика используется для создания PSI деревьев из исходного кода.

### Фаза 2: Поиск и фильтрация Kotlin файлов

#### Шаг 2.1: Рекурсивный обход

```kotlin
Files.walk(root).use { stream ->
    stream
        .filter { Files.isRegularFile(it) }
        .filter { it.toString().endsWith(".kt") }  // Только .kt, не .kts
        .filter { /* исключения */ }
        .toList()
}
```

#### Шаг 2.2: Исключения

Исключаются следующие пути:
- `.git/` - директории Git
- `build/` - директории сборки
- `out/` - выходные директории
- `node_modules/` - зависимости Node.js
- `dependencies.kt` - сгенерированные файлы зависимостей

### Фаза 3: Парсинг файлов и извлечение деклараций

#### Шаг 3.1: Парсинг файла

```kotlin
val text = Files.readString(path)
val ktFile = psiFactory.createFile(p.fileName.toString(), text)
```

- Чтение содержимого файла
- Создание PSI дерева через фабрику

#### Шаг 3.2: Извлечение метаданных файла

```kotlin
val pkgFqn = ktFile.packageFqName.asString().ifBlank { null }
val imports: List<String> = ktFile.importDirectives.mapNotNull { it.importPath?.pathStr }
```

- Пакет файла
- Список импортов

#### Шаг 3.3: Генерация команды для файла

```kotlin
visitor.onDecl(
    RawFileUnit(
        lang = SrcLang.kotlin,
        filePath = relPath,
        pkgFqn = pkgFqn,
        imports = imports,
        span = null,
        text = null,
        attributes = emptyMap()
    )
)
```

### Фаза 4: Обработка деклараций

**Метод**: `processKtFile()`

#### Шаг 4.1: Обработка типов (классы, интерфейсы, enum, object)

```kotlin
ktFile.declarations.filterIsInstance<KtClassOrObject>().forEach { decl ->
    // Определение типа
    val kindRepr = when (decl) {
        is KtObjectDeclaration -> "object"
        is KtClass -> when {
            decl.isEnum() -> "enum"
            decl.isInterface() -> "interface"
            decl.isData() -> "record"
            else -> "class"
        }
    }
    
    // FQN
    val fqn = listOfNotNull(pkgFqn, name).joinToString(".")
    
    // Супертипы
    val supertypesRepr = decl.superTypeListEntries.mapNotNull { 
        it.typeReference?.text ?: it.typeAsUserType?.text 
    }
    
    // Диапазон строк
    val span = linesOf(ktFile, decl)?.let { LineSpan(it.first, it.last) }
    
    // Исходный код
    val sourceText = decl.text
    
    // Сигнатура (до { или =)
    val signature = signatureFromDeclText(sourceText)
    
    // KDoc комментарий
    val kdocParsed = kDocFetcher.parseKDoc(decl)
    val kdocText = kdocParsed?.let { kDocFetcher.toDocString(it) }
    val kdocMeta = kDocFetcher.toMeta(kdocParsed)
    
    // Аннотации
    val annotations = getAnnotationShortNames(decl).toList()
}
```

**Генерация команды**:
```kotlin
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
        attributes = mapOf(
            RawAttrKey.FQN.key to fqn,
            RawAttrKey.SIGNATURE.key to signature,
            RawAttrKey.KDOC_TEXT.key to kdocText,
            RawAttrKey.KDOC_META.key to kdocMeta
        )
    )
)
```

#### Шаг 4.2: Обработка полей (свойства классов)

```kotlin
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
            ownerFqn = fqn,  // FQN владельца (класса)
            name = prop.name,
            typeRepr = prop.typeReference?.text,
            annotationsRepr = annotationsField,
            kdoc = pkdoc,
            span = pspan,
            text = ptext,
            attributes = emptyMap()
        )
    )
}
```

#### Шаг 4.3: Обработка функций (методы классов и top-level)

##### Для методов классов:

```kotlin
decl.declarations.filterIsInstance<KtNamedFunction>().forEach { funDecl ->
    // Проверка доступности PSI body
    if (funDecl.bodyExpression == null) {
        log.warn("PSI body for [${funDecl.name}] is NULL")
    }
    
    // Извлечение исходного кода функции
    val fsrc = extractFunctionByIndent(ktFile, funDecl)
    
    // Сбор использований (usages)
    val (rawUsages, bodyMissing) = if (funDecl.bodyExpression != null) {
        collectRawUsagesFromPsi(funDecl) to false
    } else {
        collectRawUsagesFromText(fsrc) to true  // Fallback на regex
    }
    
    // Сбор исключений (throws)
    val throwsRepr = if (funDecl.bodyExpression != null) {
        collectThrowsFromPsi(funDecl)
    } else {
        collectThrowsFromText(fsrc)  // Fallback на regex
    }
    
    // Метаданные
    val fspan = linesOf(ktFile, funDecl)?.let { LineSpan(it.first, it.last) }
    val fsig = signatureFromFunction(funDecl)
    val fkdoc = kDocFetcher.parseKDoc(funDecl)?.let { kDocFetcher.toDocString(it) }
    val annotationsFun = getAnnotationShortNames(funDecl)
    
    visitor.onDecl(
        RawFunction(
            lang = SrcLang.kotlin,
            filePath = path,
            pkgFqn = pkgFqn,
            ownerFqn = fqn,  // FQN владельца (класса)
            name = funDecl.name,
            signatureRepr = fsig,
            paramNames = funDecl.valueParameters.map { it.name ?: "_" },
            annotationsRepr = annotationsFun,
            rawUsages = rawUsages,
            throwsRepr = throwsRepr,
            kdoc = fkdoc,
            span = fspan,
            text = fsrc,
            attributes = emptyMap()
        )
    )
}
```

##### Для top-level функций:

Аналогично, но `ownerFqn = null`.

### Фаза 5: Обработка команд и построение узлов

**Сервис**: `CommandExecutorImpl.execute()`

#### Шаг 5.1: Обработка команд

Команды обрабатываются через паттерн Command:

```kotlin
when (cmd) {
    is RememberFileUnitCmd -> rememberFileUnitHandler.handle(cmd, state, builder)
    is EnsurePackageCmd -> ensurePackageHandler.handle(cmd, state, builder)
    is UpsertTypeCmd -> upsertTypeHandler.handle(cmd, state, builder)
    is UpsertFieldCmd -> upsertFieldHandler.handle(cmd, state, builder)
    is UpsertFunctionCmd -> upsertFunctionHandler.handle(cmd, state, builder)
}
```

#### Шаг 5.2: Состояние сборки (GraphState)

Кэширует созданные узлы для быстрого доступа:

```kotlin
class GraphState {
    private val packageByFqn = mutableMapOf<String, Node>()
    private val typeByFqn = mutableMapOf<String, Node>()
    private val funcByFqn = mutableMapOf<String, Node>()
    private val filePkg = mutableMapOf<String, String>()
    private val fileImports = mutableMapOf<String, List<String>>()
    private val fileUnitByPath = mutableMapOf<String, RawFileUnit>()
}
```

#### Шаг 5.3: Создание/обновление узлов

**Сервис**: `NodeBuilder.upsertNode()`

##### Валидация:

```kotlin
validateNodeData(fqn, span, parent, sourceCode)
```

- FQN не пустой и не превышает 1000 символов
- FQN соответствует формату: `^[a-zA-Z_][a-zA-Z0-9_.]*$`
- `lineStart >= 0` и `lineStart <= lineEnd`
- Parent принадлежит тому же приложению
- Защита от циклических зависимостей

##### Кэширование:

```kotlin
val existing = existingNodesCache.getOrPut(fqn) {
    nodeRepo.findByApplicationIdAndFqn(application.id, fqn)
}
```

Избегает N+1 запросов к БД.

##### Вычисление хеша:

```kotlin
val codeHash = computeCodeHash(sourceCode)  // SHA-256
```

Используется для отслеживания изменений кода.

##### Ограничение размера:

```kotlin
val normalizedSourceCode = sourceCode?.let {
    if (it.length > maxSourceCodeSize) {  // 10MB
        it.take(maxSourceCodeSize) + "\n... [truncated]"
    } else {
        it
    }
}
```

##### Нормализация строк:

```kotlin
val lineEnd = if (normalizedSourceCode?.isNotEmpty() == true && lineStart != null) {
    lineStart + countLinesNormalized(normalizedSourceCode) - 1
} else {
    span?.last
}
```

Учитывает `\r\n` → `\n`.

##### Upsert логика:

**Если нода не существует**:
```kotlin
val newNode = nodeRepo.save(
    Node(
        id = null,
        application = application,
        fqn = fqn,
        name = name,
        packageName = packageName,
        kind = kind,
        lang = lang,
        parent = parent,
        filePath = filePath,
        lineStart = lineStart,
        lineEnd = lineEnd,
        sourceCode = normalizedSourceCode,
        docComment = docComment,
        signature = signature,
        codeHash = codeHash,
        meta = metaMap
    )
)
existingNodesCache[fqn] = newNode
createdCount++
```

**Если нода существует**:
```kotlin
// Оптимизация: если codeHash не изменился, пропускаем обновление sourceCode
val codeHashChanged = existing.codeHash != codeHash

if (codeHashChanged) {
    existing.sourceCode = sourceCode
    existing.lineStart = lineStart
    existing.lineEnd = lineEnd
}

// Обновление других полей только при изменении
setIfChanged(existing.name, name) { existing.name = it }
setIfChanged(existing.packageName, packageName) { existing.packageName = it }
// ... и т.д.

if (changed) {
    val updated = nodeRepo.save(existing)
    existingNodesCache[fqn] = updated
    updatedCount++
}
```

### Фаза 6: Связывание узлов (создание рёбер)

**Сервис**: `GraphLinker.link()`

После создания всех узлов выполняется связывание:

- Разрешение ссылок по FQN
- Создание рёбер между узлами (вызовы, наследование, зависимости)
- Построение графа зависимостей

## Структура Node

```kotlin
data class Node(
    val id: Long?,                    // ID в БД
    val application: Application,     // Приложение
    val fqn: String,                  // Fully Qualified Name (уникальный)
    val name: String?,                 // Простое имя
    val packageName: String?,         // Пакет
    val kind: NodeKind,                // Тип узла (PACKAGE, CLASS, INTERFACE, FUNCTION, FIELD, etc.)
    val lang: Lang,                   // Язык (KOTLIN, JAVA, etc.)
    val parent: Node?,                // Родительский узел (иерархия)
    val filePath: String?,            // Относительный путь к файлу
    val lineStart: Int?,              // Начальная строка
    val lineEnd: Int?,                // Конечная строка
    val sourceCode: String?,          // Исходный код (до 10MB)
    val docComment: String?,           // KDoc/Javadoc комментарий
    val signature: String?,           // Сигнатура (для функций/классов)
    val codeHash: String?,             // SHA-256 хеш для отслеживания изменений
    val meta: Map<String, Any>         // Дополнительные метаданные (JSON)
)
```

## Сбор использований (usages)

### Через PSI (предпочтительно):

```kotlin
fun collectRawUsagesFromPsi(funDecl: KtNamedFunction): List<RawUsage> {
    val usages = mutableListOf<RawUsage>()
    funDecl.bodyExpression?.accept(
        object : KtTreeVisitorVoid() {
            override fun visitDotQualifiedExpression(expr: KtDotQualifiedExpression) {
                val receiver = expr.receiverExpression.text
                val memberName = expr.selectorExpression?.text
                usages.add(RawUsage.Dot(receiver, memberName, isCall))
            }
            
            override fun visitCallExpression(expr: KtCallExpression) {
                expr.calleeExpression?.text?.let { name ->
                    usages.add(RawUsage.Simple(name, isCall = true))
                }
            }
        }
    )
    return usages
}
```

### Fallback через regex (если PSI недоступен):

```kotlin
fun collectRawUsagesFromText(sourceCode: String): List<RawUsage> {
    val usages = mutableSetOf<RawUsage>()
    
    // Простые вызовы: functionName(
    val simpleCallRegex = """\b(\w+)\s*\(""".toRegex()
    simpleCallRegex.findAll(sourceCode).forEach { match ->
        usages.add(RawUsage.Simple(match.groupValues[1], isCall = true))
    }
    
    // Вызовы с точкой: receiver.method(
    val dotCallRegex = """([\w.]+\b)\.(\w+)\s*\(""".toRegex()
    dotCallRegex.findAll(sourceCode).forEach { match ->
        usages.add(RawUsage.Dot(match.groupValues[1], match.groupValues[2], isCall = true))
    }
    
    return usages.toList()
}
```

## Сбор исключений (throws)

Аналогично usages: через PSI или regex fallback.

## Схема потока данных

```
KotlinGraphBuilder.build()
  ↓
KotlinSourceWalker.walk()
  ├─→ KotlinCoreEnvironment (PSI парсер)
  ├─→ Поиск .kt файлов
  ├─→ Парсинг через PSI
  └─→ Генерация команд (RawFileUnit, RawType, RawFunction, etc.)
      ↓
CommandExecutorImpl.execute()
  ├─→ RememberFileUnitHandler
  ├─→ EnsurePackageHandler
  ├─→ UpsertTypeHandler
  ├─→ UpsertFieldHandler
  └─→ UpsertFunctionHandler
      ↓
NodeBuilder.upsertNode()
  ├─→ Валидация
  ├─→ Вычисление codeHash
  ├─→ Кэширование
  └─→ Сохранение в БД (NodeRepository)
      ↓
GraphLinker.link() - создание рёбер
```

## Оптимизации

1. **Кэширование узлов**: `existingNodesCache` избегает N+1 запросов
2. **Хеширование кода**: `codeHash` позволяет пропускать обновление неизмененного кода
3. **Транзакции**: Все операции в рамках одной транзакции
4. **Батчинг**: Сохранение узлов батчами для производительности

## Обработка ошибок

1. **PSI body == NULL**: Логируется предупреждение, используется fallback на regex
2. **Ошибка парсинга**: Логируется ошибка, файл пропускается
3. **Ошибка валидации**: Исключение `IllegalArgumentException` с описанием проблемы
4. **Ошибка сохранения**: Логируется ошибка, исключение пробрасывается выше

## Статистика

`NodeBuilder` ведет счетчики:

```kotlin
data class NodeBuilderStats(
    val created: Int,   // Создано новых узлов
    val updated: Int,   // Обновлено существующих
    val skipped: Int   // Пропущено (без изменений)
) {
    val total: Int get() = created + updated + skipped
}
```

## Связанные компоненты

- `KotlinSourceWalker` - обход исходников
- `CommandExecutorImpl` - обработка команд
- `GraphState` - состояние сборки графа
- `GraphLinker` - связывание узлов
- `NodeRepository` - доступ к БД

