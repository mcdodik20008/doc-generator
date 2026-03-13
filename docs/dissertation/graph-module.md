# Модуль построения графа знаний кода (Graph Module)

## Оглавление

1. [Введение и постановка задачи](#1-введение-и-постановка-задачи)
2. [Архитектура модуля](#2-архитектура-модуля)
3. [Стадия 0: Анализ библиотек](#3-стадия-0-анализ-библиотек)
4. [Стадия 1: Создание узлов (AST-парсинг)](#4-стадия-1-создание-узлов-ast-парсинг)
5. [Стадия 2: Построение рёбер (Edge Linking)](#5-стадия-2-построение-рёбер-edge-linking)
6. [Алгоритм разрешения типов](#6-алгоритм-разрешения-типов)
7. [Алгоритм построения графа вызовов](#7-алгоритм-построения-графа-вызовов)
8. [Обнаружение интеграционных точек](#8-обнаружение-интеграционных-точек)
9. [Семантическая классификация узлов](#9-семантическая-классификация-узлов)
10. [Параллелизация и оптимизация](#10-параллелизация-и-оптимизация)
11. [Модель данных](#11-модель-данных)
12. [Оркестрация через событийную архитектуру](#12-оркестрация-через-событийную-архитектуру)
13. [Хеширование кода и инкрементальное обновление](#13-хеширование-кода-и-инкрементальное-обновление)
14. [Тестирование](#14-тестирование)
15. [Список литературы](#15-список-литературы)

---

## 1. Введение и постановка задачи

Модуль построения графа знаний (Graph Module) решает задачу автоматического извлечения структурных и семантических знаний из исходного кода на языке Kotlin и представления их в виде **графа свойств кода** (Code Property Graph, CPG). Данный подход объединяет абстрактное синтаксическое дерево (AST), граф вызовов (Call Graph), граф зависимостей (Dependency Graph) и граф наследования (Inheritance Graph) в единую графовую структуру.

Концепция объединения множественных представлений кода в единый граф была впервые предложена Yamaguchi et al. [1] в контексте обнаружения уязвимостей, где Code Property Graph объединяет AST, граф потока управления (CFG) и граф зависимостей программы (PDG). В данной работе этот подход расширен для задач понимания программ (program comprehension), автоматической генерации документации и анализа межсервисного взаимодействия в микросервисной архитектуре.

Теоретическим основанием для использования графового представления кода служит работа Ferrante et al. [2], в которой впервые был формализован граф зависимостей программы (Program Dependence Graph, PDG), делающий эксплицитными как зависимости по данным, так и зависимости по управлению. Storey [3] в обзоре теорий и инструментов понимания программ показала, что визуальные графовые представления значительно улучшают когнитивное восприятие структуры кода разработчиками.

Ближайшим аналогом является система GraphGen4Code [4], которая строит графы знаний из исходного кода Python с узлами, представляющими классы, функции и методы, и рёбрами, описывающими отношения использования и потока данных. В отличие от GraphGen4Code, наша система:
- работает с языком Kotlin и его специфическими конструкциями;
- обнаруживает интеграционные точки между микросервисами (HTTP, Kafka, Camel);
- применяет семантическую классификацию узлов по аннотациям Spring;
- поддерживает инкрементальное обновление графа через хеширование кода;
- интегрируется с RAG-пайплайном для генерации документации с использованием LLM.

Обзор применения графов знаний в программной инженерии [5] выявил 55+ работ, покрывающих задачи интеллектуальной разработки, рекомендации кода, локализации ошибок и интеграции знаний о программных системах, что подтверждает актуальность выбранного подхода.

---

## 2. Архитектура модуля

### 2.1 Принцип разделения API/Impl

Модуль следует принципу **инверсии зависимостей** (Dependency Inversion Principle, DIP) [6] и организован по паттерну API/Impl:

```
contexts/graph/
├── api/                          # Интерфейсы и контракты
│   ├── declplanner/              # Интерфейсы планировщиков (Strategy)
│   │   └── DeclPlanner.kt
│   ├── node/                     # Интерфейсы обхода (Visitor)
│   │   └── SourceVisitor.kt
│   ├── linker/                   # Интерфейсы линковщиков (Strategy)
│   │   └── EdgeLinker.kt
│   └── model/                    # Сырые декларации (RawDecl)
│       ├── RawDecl.kt
│       └── RawUsage.kt
└── impl/                         # Реализации
    ├── declplanner/              # 5 планировщиков
    ├── node/                     # AST-обход и построение узлов
    │   ├── KotlinSourceWalker.kt
    │   ├── KotlinToDomainVisitor.kt
    │   ├── CommandExecutorImpl.kt
    │   └── builder/
    │       └── NodeBuilder.kt
    ├── linker/                   # 7 линковщиков рёбер
    │   ├── GraphLinkerImpl.kt
    │   ├── StructuralEdgeLinker.kt
    │   ├── InheritanceEdgeLinker.kt
    │   ├── AnnotationEdgeLinker.kt
    │   ├── SignatureDependencyLinker.kt
    │   ├── CallEdgeLinker.kt
    │   ├── ThrowEdgeLinker.kt
    │   └── IntegrationEdgeLinker.kt
    └── nodekindextractor/        # 12+ экстракторов классификации
```

Поток зависимостей модулей: `impl -> api -> kernel`, что гарантирует, что реализации зависят только от абстракций, а не наоборот.

### 2.2 Трёхстадийный процесс построения

Построение графа выполняется в три последовательные стадии:

| Стадия | Вход | Выход | Основной алгоритм |
|--------|------|-------|-------------------|
| **Стадия 0** | JAR-файлы библиотек | LibraryNode (интеграционные паттерны) | Анализ байткода |
| **Стадия 1** | .kt исходные файлы | Node (узлы графа) | AST-парсинг + Visitor |
| **Стадия 2** | Множество Node | Edge (рёбра графа) | Параллельное связывание |

Разделение на стадии обеспечивает:
- **Изоляцию ошибок**: ошибка при создании рёбер не откатывает созданные узлы;
- **Масштабируемость**: каждая стадия может быть оптимизирована независимо;
- **Переиспользуемость**: индекс узлов, построенный на Стадии 1, используется на Стадии 2.

---

## 3. Стадия 0: Анализ библиотек

На данной стадии выполняется анализ байткода внешних JAR-зависимостей для обнаружения интеграционных паттернов. Система извлекает из библиотек информацию о:
- HTTP-клиентах (WebClient, Feign, RestTemplate);
- продюсерах и консьюмерах Kafka;
- маршрутах Apache Camel.

Результатом является набор `LibraryNode`, хранящихся в `LibraryNodeIndex` — индексе, который используется на Стадии 2 для связывания вызовов библиотечных методов с интеграционными точками.

Подход к раздельному анализу библиотечного и прикладного кода обоснован работой Ali & Lhotak [7], которые показали, что построение графа вызовов только по прикладному коду (без анализа библиотечного) является эффективной стратегией, позволяющей значительно снизить вычислительные затраты при сохранении достаточной точности.

---

## 4. Стадия 1: Создание узлов (AST-парсинг)

### 4.1 Архитектура парсинга: паттерн Visitor

Для обхода абстрактного синтаксического дерева используется **паттерн Visitor** [8], формализованный Gamma et al. в каталоге GoF. Palsberg & Jay [9] исследовали сущность данного паттерна и продемонстрировали его применимость для обработки AST без необходимости модификации классов узлов дерева.

Интерфейс `SourceVisitor` определяет контракт для обхода AST:

```kotlin
interface SourceVisitor {
    fun onDecl(raw: RawDecl)       // Диспетчеризация по типу декларации
    fun onFile(unit: RawFileUnit)  // Единица компиляции (файл)
    fun onPackage(decl: RawPackage)
    fun onType(decl: RawType)      // Класс, интерфейс, enum, record
    fun onField(decl: RawField)    // Поле, свойство
    fun onFunction(decl: RawFunction) // Метод, функция
}
```

Этот интерфейс является **языконезависимым**: набор `RawDecl` описывает универсальные конструкции программирования (файл, пакет, тип, функция, поле), что позволяет в будущем реализовать поддержку других языков (Java, Go, TypeScript) без изменения архитектуры обхода.

### 4.2 Парсинг Kotlin AST через PSI

Реализация `KotlinSourceWalker` использует **Kotlin Compiler API** и **Program Structure Interface (PSI)** [10] — технологию JetBrains для построения синтаксических и семантических моделей кода. PSI-элементы формируют иерархию (PSI-дерево), в которой каждый узел представляет синтаксическую конструкцию: `KtClass`, `KtNamedFunction`, `KtProperty` и т.д.

Архитектура компилятора Kotlin [11] предусматривает следующий пайплайн: **Исходный текст -> Лексический анализ -> PSI-дерево -> FIR (Frontend Intermediate Representation) -> Backend IR**. Наша система работает на уровне PSI, что обеспечивает доступ к полной синтаксической структуре без необходимости полной компиляции.

Процесс обхода:

```
KotlinSourceWalker.walk(sourceRoot, visitor, classpath)
  │
  ├── Фильтрация директорий (.git, build, out, node_modules)
  │
  ├── Для каждого .kt файла:
  │   ├── Создание KotlinCoreEnvironment + KotlinPsiFactory
  │   ├── Парсинг в PSI-дерево
  │   ├── Извлечение: imports, package, declarations
  │   └── Генерация: RawFileUnit, RawPackage, RawType, RawFunction, RawField
  │
  └── Передача каждого RawDecl в visitor.onDecl()
```

Как описано в учебнике Aho et al. [12] ("Dragon Book"), синтаксически-направленная трансляция (syntax-directed translation) преобразует синтаксическое дерево в целевое представление, что является классическим подходом в компиляторостроении. В нашей системе целевым представлением является граф знаний (Knowledge Graph).

### 4.3 Промежуточное представление: RawDecl

Промежуточное представление `RawDecl` (sealed interface) сохраняет **сырую** структуру кода до нормализации:

```kotlin
sealed interface RawDecl

data class RawFileUnit(
    val lang: Lang,
    val filePath: String,
    val pkgFqn: String,
    val imports: List<String>,
    val text: String,
    val span: IntRange?
) : RawDecl

data class RawType(
    val simpleName: String,
    val kindRepr: String,         // "class", "interface", "enum", "object"
    val supertypesRepr: List<String>,
    val annotationsRepr: List<String>,
    val text: String,
    val span: IntRange?
) : RawDecl

data class RawFunction(
    val name: String,
    val ownerFqn: String,
    val paramNames: List<String>,
    val paramTypeNames: List<String>,
    val rawUsages: List<RawUsage>,  // Обнаруженные использования в теле
    val throwsRepr: List<String>,
    val kdoc: String?,
    val text: String,
    val span: IntRange?
) : RawDecl
```

Ключевое проектное решение — **хранение сырых ссылок** (`supertypesRepr`, `paramTypeNames`) вместо разрешённых FQN. Разрешение типов выполняется отложенно на Стадии 2, когда построен полный индекс узлов. Этот подход минимизирует связность между стадиями.

### 4.4 Паттерн Strategy: планировщики деклараций

Преобразование `RawDecl` в команды создания узлов выполняется через **паттерн Strategy** [8]:

```kotlin
interface DeclPlanner<T : RawDecl> {
    val target: KClass<T>        // Тип обрабатываемой декларации
    fun plan(raw: T): List<DeclCmd>  // Генерация команд
}
```

Пять реализаций:

| Планировщик | Вход | Выходная команда | Целевой NodeKind |
|-------------|------|------------------|------------------|
| `TypePlanner` | `RawType` | `UpsertTypeCmd` | CLASS, INTERFACE, ENUM, RECORD |
| `FunctionPlanner` | `RawFunction` | `UpsertFunctionCmd` | METHOD |
| `FieldPlanner` | `RawField` | `UpsertFieldCmd` | FIELD |
| `PackagePlanner` | `RawPackage` | `EnsurePackageCmd` | PACKAGE |
| `FileUnitPlanner` | `RawFileUnit` | `RememberFileUnitCmd` | (контекст файла) |

Паттерн Strategy позволяет добавлять новые типы деклараций (например, для поддержки Kotlin DSL или Compose-функций) без модификации существующего кода диспетчеризации.

### 4.5 Паттерн Command: выполнение команд

Команды (`DeclCmd`) выполняются через `CommandExecutorImpl`, реализующий **паттерн Command** [8], впервые описанный Lieberman [13] в контексте интерактивных систем и формализованный в [8]:

```kotlin
class CommandExecutorImpl(...) : CommandExecutor {
    private val state = GraphState()  // Контекст текущего файла

    override fun execute(cmd: DeclCmd) {
        when (cmd) {
            is RememberFileUnitCmd -> rememberFileUnitHandler.handle(cmd, state)
            is EnsurePackageCmd    -> ensurePackageHandler.handle(cmd, state)
            is UpsertTypeCmd       -> upsertTypeHandler.handle(cmd, state)
            is UpsertFieldCmd      -> upsertFieldHandler.handle(cmd, state)
            is UpsertFunctionCmd   -> upsertFunctionHandler.handle(cmd, state)
        }
    }
}
```

`GraphState` хранит контекст текущего файла (импорты, пакет, путь), который разделяется между обработчиками. Это устраняет необходимость передавать контекст через каждую команду.

### 4.6 Паттерн Registry: динамическая диспетчеризация

`KotlinToDomainVisitor` использует **паттерн Registry** для динамического сопоставления типов `RawDecl` с планировщиками:

```kotlin
class KotlinToDomainVisitor(
    private val exec: CommandExecutor,
    planners: List<DeclPlanner<*>>
) : SourceVisitor {

    private val registry: Map<KClass<out RawDecl>, DeclPlanner<out RawDecl>> =
        planners.associateBy { it.target }

    override fun onDecl(raw: RawDecl) {
        val handler = registry[raw::class] as? DeclPlanner<RawDecl> ?: return
        handler.plan(raw).forEach(exec::execute)
    }
}
```

Планировщики регистрируются как Spring-компоненты (`@Component`) и автоматически внедряются через конструктор, что обеспечивает расширяемость: добавление нового `DeclPlanner` не требует модификации `KotlinToDomainVisitor`.

### 4.7 Создание и обновление узлов: NodeBuilder

`NodeBuilder` реализует логику **upsert** (создание или обновление) узлов:

```kotlin
fun upsertNode(fqn, kind, name, ..., meta): Node {
    val existing = nodeCache.getOrCompute(fqn) {
        nodeRepo.findByApplicationIdAndFqn(...)
    }
    val normalizedCode = codeNormalizer.normalize(sourceCode)
    val codeHash = codeHasher.computeHash(normalizedCode)

    return if (existing == null) {
        createNewNode(...)
    } else if (existing.codeHash != codeHash) {
        updateExistingNode(...)
    } else {
        existing  // Без изменений — пропуск
    }
}
```

Ключевые механизмы:
- **NodeCache**: кеш для предотвращения N+1 запросов к базе данных;
- **CodeNormalizer**: нормализация кода (удаление комментариев, пробелов) перед хешированием;
- **CodeHasher**: вычисление SHA-256 хеша для детекции изменений.

---

## 5. Стадия 2: Построение рёбер (Edge Linking)

### 5.1 Архитектура линковщиков

Построение рёбер выполняется через набор **линковщиков** (EdgeLinker), реализующих **паттерн Strategy** [8]:

```kotlin
interface EdgeLinker {
    fun link(node: Node, meta: NodeMeta, index: NodeIndex):
        List<Triple<Node, Node, EdgeKind>>
}
```

Семь линковщиков, каждый из которых отвечает за определённую категорию рёбер:

| # | Линковщик | Типы рёбер | Описание |
|---|-----------|-----------|----------|
| 1 | `StructuralEdgeLinker` | CONTAINS | Иерархическое вложение: пакет -> тип -> метод/поле |
| 2 | `InheritanceEdgeLinker` | INHERITS, IMPLEMENTS, DEPENDS_ON | Наследование и реализация интерфейсов |
| 3 | `AnnotationEdgeLinker` | ANNOTATED_WITH, DEPENDS_ON | Связи с аннотациями |
| 4 | `SignatureDependencyLinker` | DEPENDS_ON | Типовые зависимости из сигнатур методов |
| 5 | `CallEdgeLinker` | CALLS_CODE | Вызовы методов (см. раздел 7) |
| 6 | `ThrowEdgeLinker` | THROWS | Бросаемые исключения |
| 7 | `IntegrationEdgeLinker` | CALLS_HTTP, PRODUCES, CONSUMES, RETRIES_TO, CIRCUIT_BREAKER_TO | Интеграционные связи (см. раздел 8) |

### 5.2 Процесс линковки: GraphLinkerImpl

Центральный координатор `GraphLinkerImpl` выполняет линковку в два этапа:

```kotlin
@Transactional
override fun link(application: Application) {
    // 1. Загрузка всех узлов приложения
    val all = nodeRepo.findAllByApplicationId(appId, Pageable.ofSize(maxNodes))

    // 2. Создание мутабельного индекса
    val index = nodeIndexFactory.createMutable(all)

    // 3. Структурная линковка (последовательно)
    val structuralEdges = structuralEdgeLinker.linkContains(all, index, ::metaOf)

    // 4. Параллельная линковка остальных рёбер (ForkJoinPool)
    val parallelism = Runtime.getRuntime().availableProcessors().coerceAtMost(8)
    val customPool = ForkJoinPool(parallelism)
    val results = customPool.submit<List<NodeLinkResult>> {
        all.parallelStream()
            .map { node -> linkSingleNode(node, metaOf(node), index, application) }
            .toList()
    }.get(10, TimeUnit.MINUTES)

    // 5. Агрегация и batch-сохранение
    edgeRepository.upsertAll(allEdges)
}
```

Структурная линковка выполняется **последовательно**, так как требует обзора всех узлов для построения иерархии CONTAINS. Остальные линковщики работают **параллельно**, поскольку каждый узел обрабатывается независимо.

---

## 6. Алгоритм разрешения типов

### 6.1 Постановка задачи

Разрешение типов (name resolution / type resolution) — задача определения, какой конкретный узел графа соответствует ссылке на тип, указанной в исходном коде. Neron et al. [14] предложили формальную теорию разрешения имён на основе **графов областей видимости** (scope graphs), доказав корректность и полноту алгоритма разрешения.

Konat et al. [15] идентифицировали повторяющиеся паттерны привязки имён в языках программирования и ввели декларативный метаязык для их спецификации, что обеспечивает языко-параметрический подход к разрешению.

### 6.2 Реализация: четырёхшаговый алгоритм

В нашей системе алгоритм разрешения реализован в `NodeIndexFactory` (класс `MutableNodeIndex`) и использует **цепочку фоллбэков** (fallback chain):

```
function resolveType(simpleOrFqn, imports, pkg):

    // Шаг 1: Точное совпадение по FQN
    if byFqn[simpleOrFqn] exists:
        return byFqn[simpleOrFqn]

    // Шаг 2: Извлечение простого имени и поиск по импортам
    simple := extractSimpleName(simpleOrFqn)  // "List<String>" -> "List"
    for each import in imports:
        if import endsWith("." + simple):
            if byFqn[import] exists:
                return byFqn[import]

    // Шаг 3: Поиск в текущем пакете
    if byFqn[pkg + "." + simple] exists:
        return byFqn[pkg + "." + simple]

    // Шаг 4: Фоллбэк — первое совпадение по простому имени
    return bySimple[simple]?.firstOrNull()
```

Очистка имени типа включает:
- Удаление суффикса nullable: `String?` -> `String`
- Удаление параметров generics: `List<String>` -> `List`
- Извлечение простого имени из FQN: `com.example.MyClass` -> `MyClass`

### 6.3 Индексные структуры

Для быстрого разрешения используются три индекса:

| Индекс | Тип | Назначение |
|--------|-----|------------|
| `byFqn` | `Map<String, Node>` | Точный поиск по полному квалифицированному имени |
| `bySimple` | `Map<String, List<Node>>` | Поиск по простому имени (может быть неоднозначным) |
| `byBaseFqn` | `Map<String, List<Node>>` | Поиск методов по базовому FQN класса-владельца |

Индекс `MutableNodeIndex` допускает **динамическое добавление узлов** во время линковки. Это критично для `IntegrationEdgeLinker`, который создаёт виртуальные узлы (ENDPOINT, TOPIC) на лету.

---

## 7. Алгоритм построения графа вызовов

### 7.1 Теоретические основы

Построение графа вызовов (call graph construction) — фундаментальная задача статического анализа. Ryder [16] предложила первый формальный алгоритм построения графа вызовов и доказала его корректность. Grove & Chambers [17] представили унифицирующий параметризованный фреймворк для описания алгоритмов построения графа вызовов (CHA, RTA, points-to), покрывающий компромисс между стоимостью и точностью. Tip & Palsberg [18] рассмотрели проблему масштабируемости для больших кодовых баз.

### 7.2 Извлечение использований: RawUsage

На Стадии 1 из тел методов извлекаются **паттерны использования** (`RawUsage`), представляющие потенциальные вызовы:

```kotlin
sealed class RawUsage {
    // Простой вызов: foo() или print
    data class Simple(
        val name: String,
        val isCall: Boolean
    ) : RawUsage()

    // Вызов через точку: obj.method() или ClassName.staticMethod()
    data class Dot(
        val receiver: String,
        val member: String,
        val isCall: Boolean = true
    ) : RawUsage()
}
```

### 7.3 Алгоритм связывания вызовов: CallEdgeLinker

`CallEdgeLinker` реализует **два паттерна разрешения**:

**Паттерн 1: Simple Usage** (`foo()`)

```
function resolveSimpleUsage(usage, ownerFqn, index):
    // Попытка 1: Поиск в классе-владельце
    target := index.byFqn[ownerFqn + "." + usage.name]
    if target != null:
        return CALLS_CODE(node, target)

    // Попытка 2: Поиск через импорты (если вызов)
    if usage.isCall:
        target := index.resolveType(usage.name, imports, pkg)
        if target != null:
            return CALLS_CODE(node, target)

    return null  // Не разрешён (мягкая ошибка)
```

**Паттерн 2: Dot Usage** (`obj.method()`)

```
function resolveDotUsage(usage, ownerFqn, index):
    // Шаг 1: Разрешение типа получателя
    if usage.receiver[0].isUpperCase():
        // Ссылка на тип: ClassName.method()
        receiverType := index.resolveType(usage.receiver, imports, pkg)
    else:
        // Ссылка на переменную: используем тип владельца
        receiverType := index.byFqn[ownerFqn]

    if receiverType == null:
        return null

    // Шаг 2: Поиск метода в типе получателя
    target := index.byFqn[receiverType.fqn + "." + usage.member]

    // Шаг 3: Фоллбэк — поиск перегруженного метода
    if target == null:
        candidates := index.byBaseFqn[receiverType.fqn]
        target := candidates?.find { it.name == usage.member }

    if target != null:
        return CALLS_CODE(node, target)

    return null
```

Подход к разрешению с заглавной/строчной буквы получателя реализует **Class Hierarchy Analysis (CHA)**, описанный Dean et al. [19]: если получатель — это имя типа (заглавная буква), выполняется прямой поиск в иерархии классов; если имя переменной (строчная буква) — используется контекст владельца.

### 7.4 Обработка ошибок разрешения

Ошибки разрешения вызовов являются **мягкими** (soft errors): неразрешённые вызовы логируются, но не прерывают процесс. Это критично для обработки вызовов стандартной библиотеки и внешних библиотек, узлы которых могут отсутствовать в графе.

---

## 8. Обнаружение интеграционных точек

### 8.1 Мотивация

Анализ межсервисного взаимодействия в микросервисной архитектуре является активной областью исследований. Bushong et al. [20] продемонстрировали применение статического анализа для восстановления архитектуры микросервисов, включая генерацию коммуникационных диаграмм и карт контекстов. Cerny et al. [21] описали полный пайплайн от статического анализа кода до визуальных архитектурных моделей микросервисов.

Abdelfattah & Cerny [22] ввели понятие **Endpoint Dependency Matrix (EDM)** и **Data Dependency Matrix (DDM)** для отслеживания зависимостей между микросервисами через конечные точки, что концептуально аналогично нашему подходу с виртуальными узлами.

### 8.2 Виртуальные узлы

Интеграционные точки представлены **виртуальными узлами** — узлами графа, не имеющими прямого представления в исходном коде:

| Тип | Паттерн FQN | NodeKind | Пример |
|-----|-------------|----------|--------|
| HTTP endpoint | `infra:http:METHOD:URL` | ENDPOINT | `infra:http:GET:/api/users` |
| Kafka topic | `infra:kafka:topic:NAME` | TOPIC | `infra:kafka:topic:order-events` |
| Camel route | `infra:camel:uri:URI` | ENDPOINT | `infra:camel:uri:direct:process` |

Виртуальные узлы маркируются метаданным `synthetic: true`, что позволяет отличать их от узлов, извлечённых из исходного кода.

### 8.3 Алгоритм IntegrationEdgeLinker

```
function linkIntegration(node, meta, index, libIndex):
    edges := []
    virtualNodes := []

    for each usage in meta.rawUsages:
        // Шаг 1: Разрешение FQN библиотечного метода
        libMethodFqn := resolveLibraryFqn(usage, meta)
        libNode := libIndex.find(libMethodFqn)

        if libNode == null:
            continue

        // Шаг 2: Извлечение интеграционных точек
        for each point in extractIntegrationPoints(libNode):
            switch point.type:
                case HTTP_ENDPOINT:
                    virtualNode := getOrCreateEndpointNode(
                        fqn = "infra:http:" + point.method + ":" + point.url,
                        index = index
                    )
                    edges.add(CALLS_HTTP(node, virtualNode))

                    // Паттерны отказоустойчивости
                    if point.hasRetry:
                        edges.add(RETRIES_TO(node, virtualNode))
                    if point.hasCircuitBreaker:
                        edges.add(CIRCUIT_BREAKER_TO(node, virtualNode))
                    if point.hasTimeout:
                        edges.add(TIMEOUTS_TO(node, virtualNode))

                case KAFKA_TOPIC:
                    virtualNode := getOrCreateTopicNode(
                        fqn = "infra:kafka:topic:" + point.topicName,
                        index = index
                    )
                    edge := if point.operation == "PRODUCE"
                        then PRODUCES(node, virtualNode)
                        else CONSUMES(node, virtualNode)
                    edges.add(edge)

    return edges, virtualNodes
```

### 8.4 Рёбра паттернов отказоустойчивости

Система обнаруживает паттерны отказоустойчивости (resilience patterns), создавая специализированные рёбра:

| Ребро | Описание |
|-------|----------|
| `RETRIES_TO` | Повторные попытки вызова при ошибке |
| `CIRCUIT_BREAKER_TO` | Автоматический разрыв цепи при каскадных сбоях |
| `TIMEOUTS_TO` | Таймауты при ожидании ответа |

Это позволяет визуализировать и анализировать паттерны устойчивости на уровне архитектуры.

---

## 9. Семантическая классификация узлов

### 9.1 Паттерн Chain of Responsibility: NodeKindRefiner

Классификация узлов по семантическому типу (`NodeKind`) выполняется через **паттерн Chain of Responsibility** [8], реализованный как цепочка экстракторов:

```kotlin
val kind = extractors
    .asSequence()
    .filter { it.supports(ctx.lang) }        // Фильтрация по языку
    .sortedBy { it.priority }                 // Сортировка по приоритету
    .mapNotNull { it.refineType(baseKind, raw, ctx) }
    .firstOrNull() ?: baseKind               // Фоллбэк на базовый тип
```

### 9.2 Экстракторы

12+ экстракторов, упорядоченных по приоритету:

| # | Экстрактор | Триггер | Результат | Приоритет |
|---|-----------|---------|-----------|-----------|
| 1 | `TestClassExtractor` | `@Test`, `@SpringBootTest` | TEST | 0 (наивысший) |
| 2 | `ServiceLayerExtractor` | `@Service` | SERVICE | 10 |
| 3 | `EndpointClassExtractor` | `@RestController`, `@RequestMapping` | ENDPOINT | 20 |
| 4 | `MapperExtractor` | `@Mapper` (MapStruct) | MAPPER | 30 |
| 5 | `RepositoryExtractor` | extends `JpaRepository` | DB_TABLE | 40 |
| 6 | `ClientExtractor` | `WebClient`, `@FeignClient` | CLIENT | 50 |
| 7 | `ConfigExtractor` | `@Configuration`, `@ConfigurationProperties` | CONFIG | 60 |
| 8 | `JobWorkerExtractor` | `@Scheduled`, `@KafkaListener` | JOB | 70 |
| 9 | `DatabaseRelatedExtractor` | Паттерны миграций, схем | DB_TABLE/MIGRATION | 80 |
| 10 | `ExceptionTypeExtractor` | extends `Exception`/`Throwable` | EXCEPTION | 90 |
| 11 | `AnnotationExtractor` | `@interface` | ANNOTATION | 100 |
| 12 | `SchemaExtractor` | Схемы Avro, Protobuf | SCHEMA | 110 |

Тестовые классы имеют **наивысший приоритет** (0), поскольку тест-класс с аннотацией `@Service` должен классифицироваться как TEST, а не как SERVICE.

### 9.3 Сбор метаданных API: ApiMetadataCollector

`ApiMetadataCollector` извлекает из аннотаций Spring метаданные REST/gRPC/Kafka, сохраняемые в `Node.meta.apiMetadata`:

```kotlin
data class ApiMetadata(
    val httpMethod: String?,     // GET, POST, PUT, DELETE
    val path: String?,           // /api/users/{id}
    val produces: List<String>,  // application/json
    val consumes: List<String>,  // application/json
    val kafkaTopic: String?,
    val kafkaGroup: String?
)
```

---

## 10. Параллелизация и оптимизация

### 10.1 Fork/Join Framework

Параллельная обработка на Стадии 2 реализована через `ForkJoinPool` Java, разработанный Doug Lea [23]. В основе лежит алгоритм **work-stealing** (похищение работы), формализованный Blumofe & Leiserson [24]: каждый рабочий поток имеет собственную деку задач, и свободные потоки «похищают» задачи у загруженных.

Конфигурация:
```kotlin
val parallelism = Runtime.getRuntime().availableProcessors().coerceAtMost(8)
val customPool = ForkJoinPool(parallelism)
```

Ограничение в 8 потоков предотвращает чрезмерную конкуренцию за разделяемый `MutableNodeIndex`.

### 10.2 Стратегии оптимизации

| Стратегия | Описание | Эффект |
|-----------|----------|--------|
| **NodeCache** | Кеш узлов в NodeBuilder | Предотвращение N+1 запросов к БД |
| **Batch upsert** | Все рёбра сохраняются одним batch | Снижение количества запросов к БД |
| **Индексирование** | 3 индекса: byFqn, bySimple, byBaseFqn | O(1) поиск вместо O(n) |
| **Мутабельный индекс** | Динамическое добавление виртуальных узлов | Без пересоздания индекса |
| **Пагинация** | `docgen.graph.max-nodes` (по умолчанию 100,000) | Защита от OOM |
| **Таймаут** | 10 минут на параллельную фазу | Защита от зависания |
| **Прогресс** | Логирование каждые 5% | Наблюдаемость |

### 10.3 Защитные механизмы

- **Мягкие ошибки**: ошибки разрешения типов и вызовов логируются, но не прерывают процесс;
- **Порог ошибок**: при 100+ ошибках генерируется предупреждение;
- **Транзакционность**: стадии выполняются в отдельных транзакциях — откат одной стадии не влияет на другую.

---

## 11. Модель данных

### 11.1 Узел (Node)

```kotlin
@Entity
class Node(
    val fqn: String,              // Уникальный в пределах приложения
    val name: String?,            // Человекочитаемое имя
    val packageName: String?,     // Пакет
    val kind: NodeKind,           // Семантический тип (21 вариант)
    val lang: Lang,               // Язык (KOTLIN, JAVA, ...)
    val sourceCode: String?,      // Исходный код
    val signature: String?,       // Сигнатура (для методов)
    val codeHash: String?,        // SHA-256 хеш нормализованного кода
    val meta: Map<String, Any>,   // JSONB: NodeMeta
    val application: Application, // Связь с приложением
    val parent: Node?,            // Иерархический родитель
    val filePath: String?,        // Путь к файлу
    val lineStart: Int?,          // Начальная строка
    val lineEnd: Int?             // Конечная строка
)
```

**Уникальное ограничение**: `(application_id, fqn)` — в пределах одного приложения каждый FQN уникален.

### 11.2 Ребро (Edge)

```kotlin
@Entity @IdClass(EdgeId::class)
class Edge(
    val src: Node,                    // Исходный узел
    val dst: Node,                    // Целевой узел
    val kind: EdgeKind,               // Тип связи (26 вариантов)
    val evidence: Map<String, Any>,   // Контекст/обоснование
    val explainMd: String?,           // LLM-объяснение связи
    val confidence: BigDecimal?       // Уверенность (0.00–1.00)
)
```

**Уникальное ограничение**: `(src_id, dst_id, kind)` — между двумя узлами может быть не более одного ребра каждого типа.

### 11.3 Классификация узлов (NodeKind) — 21 тип

| Категория | Типы |
|-----------|------|
| **Структурные** | REPO, MODULE, PACKAGE |
| **ООП** | CLASS, INTERFACE, ENUM, RECORD, METHOD, FIELD, ANNOTATION |
| **Специализированные** | EXCEPTION, TEST |
| **Сервисные** | SERVICE, ENDPOINT, CLIENT, JOB, CONFIG |
| **Интеграционные** | TOPIC, MAPPER |
| **Данные** | DB_TABLE, DB_VIEW, DB_QUERY, SCHEMA, MIGRATION |

### 11.4 Классификация рёбер (EdgeKind) — 26 типов

| Категория | Типы |
|-----------|------|
| **Структурные** | CONTAINS |
| **ООП** | INHERITS, IMPLEMENTS, EXTENDS, OVERRIDES, ANNOTATED_WITH, DEPENDS_ON |
| **Вызовы** | CALLS, CALLS_CODE, THROWS, LOCKS |
| **Интеграционные** | CALLS_HTTP, CALLS_CAMEL, CALLS_GRPC, PRODUCES, CONSUMES, QUERIES, READS, WRITES |
| **Контрактные** | CONTRACTS_WITH, CONFIGURES |
| **Отказоустойчивость** | CIRCUIT_BREAKER_TO, RETRIES_TO, TIMEOUTS_TO |

---

## 12. Оркестрация через событийную архитектуру

### 12.1 Паттерн Observer / Publish-Subscribe

Стадии построения графа оркестрируются через **Spring Application Events**, реализующие паттерн **Observer** [8]. Eugster et al. [25] выделили ключевое свойство pub/sub-систем: полная развязка взаимодействующих компонентов по времени, пространству и синхронизации.

```
GraphBuildRequestedEvent
    └── GraphBuildEventListener
        └── KotlinGraphBuilder.build()
            ├── Стадия 1: Node creation
            └── Стадия 2: Edge linking

LinkRequestedEvent
    └── LinkEventListener
        └── GraphLinker.link()

LibraryBuildRequestedEvent
    └── LibraryBuildEventListener
        └── Library analysis (Stage 0)
```

Асинхронная обработка событий обеспечивает:
- **Развязку** между API-контроллерами и графовым движком;
- **Масштабируемость**: возможность обработки нескольких приложений параллельно;
- **Наблюдаемость**: каждое событие логируется с метриками.

---

## 13. Хеширование кода и инкрементальное обновление

### 13.1 Мотивация

Dietrich et al. [26] в работе, получившей **Best Paper Award** на USENIX ATC 2017, показали, что хеширование AST (вместо исходного текста) позволяет исключить 80% избыточных компиляций и сократить время инкрементальной сборки до 51%.

### 13.2 Реализация

В нашей системе используется двухэтапный подход:

1. **Нормализация кода** (`CodeNormalizer`): удаление комментариев, нормализация пробелов, удаление пустых строк;
2. **Хеширование** (`CodeHasher`): вычисление SHA-256 от нормализованного кода.

```kotlin
class CodeHasherImpl : CodeHasher {
    override fun computeHash(normalizedCode: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(normalizedCode.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
```

При повторном анализе:
- Если `codeHash` совпадает — узел не обновляется (**skip**);
- Если `codeHash` отличается — узел обновляется, а связанная документация (`NodeDoc`) помечается как устаревшая.

Smits et al. [27] рассмотрели вопросы отслеживания зависимостей, кеширования и инвалидации кеша при инкрементальной компиляции, что релевантно для обработки каскадных обновлений при изменении разделяемых модулей.

---

## 14. Тестирование

Модуль покрыт комплексным набором тестов (45+ тестовых классов):

### 14.1 Модульные тесты

| Компонент | Тестовый класс | Покрытие |
|-----------|---------------|----------|
| Индексы | `NodeIndexFactoryTest`, `NodeIndexFactoryMutableTest` | Все 4 шага разрешения типов |
| Линковщики | `CallEdgeLinkerTest`, `AnnotationEdgeLinkerTest`, `InheritanceEdgeLinkerTest`, ... | Все 7 линковщиков |
| Хеширование | `CodeHasherImplTest`, `CodeNormalizerImplTest` | Нормализация + хеширование |
| Построитель | `NodeBuilderTest` | Логика upsert |
| Парсинг | `KotlinSourceWalkerTest` | Обход файловой системы |

### 14.2 Интеграционные тесты

| Компонент | Тестовый класс | Что проверяется |
|-----------|---------------|-----------------|
| Линковка | `GraphLinkerImplTest` | Полный цикл линковки |
| Построение | `KotlinGraphBuilderTest` | Стадии 1+2 end-to-end |
| Библиотеки | `LibraryNodeIndexImplTest` | Индекс библиотечных узлов |

### 14.3 Тесты экстракторов

12 тестовых классов покрывают каждый `NodeKindExtractor`:
`ServiceLayerExtractorTest`, `EndpointClassExtractorTest`, `MapperExtractorTest`, `RepositoryExtractorTest`, `ClientExtractorTest`, `ConfigExtractorTest`, `JobWorkerExtractorTest`, `TestClassExtractorTest`, `ExceptionTypeExtractorTest`, `AnnotationExtractorTest`, `SchemaExtractorTest`, `DatabaseRelatedExtractorTest`.

Технологии тестирования: JUnit 5, MockK, Mockito, AssertJ, TestContainers.

---

## 15. Список литературы

[1] Yamaguchi, F., Golde, N., Arp, D., Rieck, K. *Modeling and Discovering Vulnerabilities with Code Property Graphs.* In: IEEE Symposium on Security and Privacy (S&P 2014). IEEE, 2014. DOI: [10.1109/SP.2014.44](https://ieeexplore.ieee.org/document/6956589)

[2] Ferrante, J., Ottenstein, K.J., Warren, J.D. *The Program Dependence Graph and Its Use in Optimization.* ACM Transactions on Programming Languages and Systems (TOPLAS), Vol. 9, No. 3, pp. 319-349, 1987. DOI: [10.1145/24039.24041](https://dl.acm.org/doi/10.1145/24039.24041)

[3] Storey, M.-A. *Theories, Tools and Research Methods in Program Comprehension: Past, Present and Future.* Software Quality Journal, Springer, Vol. 14, pp. 187-208, 2006. DOI: [10.1007/s11219-006-9216-4](https://link.springer.com/article/10.1007/s11219-006-9216-4)

[4] Srinivas, K., Abdelaziz, I., Dolby, J., McCusker, J.P. *A Toolkit for Generating Code Knowledge Graphs (GraphGen4Code).* In: 11th Knowledge Capture Conference (K-CAP 2021). ACM, 2021. DOI: [10.1145/3460210.3493578](https://dl.acm.org/doi/10.1145/3460210.3493578)

[5] *Application of Knowledge Graph in Software Engineering Field: A Systematic Literature Review.* Information and Software Technology, Elsevier, 2023. URL: [ScienceDirect](https://www.sciencedirect.com/science/article/abs/pii/S0950584923001829)

[6] Martin, R.C. *Agile Software Development: Principles, Patterns, and Practices.* Prentice Hall, 2002. ISBN: 0-13-597444-5.

[7] Ali, K., Lhotak, O. *Application-Only Call Graph Construction.* In: ECOOP 2012, LNCS Vol. 7313, pp. 688-712. Springer, 2012. DOI: [10.1007/978-3-642-31057-7_30](https://link.springer.com/chapter/10.1007/978-3-642-31057-7_30)

[8] Gamma, E., Helm, R., Johnson, R., Vlissides, J. *Design Patterns: Elements of Reusable Object-Oriented Software.* Addison-Wesley Professional, 1994. ISBN: 0-201-63361-2.

[9] Palsberg, J., Jay, C.B. *The Essence of the Visitor Pattern.* In: 22nd COMPSAC, pp. 9-15. IEEE, 1998. DOI: [10.1109/CMPSAC.1998.716629](https://ieeexplore.ieee.org/document/716629/)

[10] JetBrains. *Program Structure Interface (PSI) — IntelliJ Platform Plugin SDK Documentation.* URL: [plugins.jetbrains.com/docs/intellij/psi.html](https://plugins.jetbrains.com/docs/intellij/psi.html)

[11] JetBrains Kotlin Team. *K2 Compiler Migration Guide.* Kotlin Official Documentation. URL: [kotlinlang.org/docs/k2-compiler-migration-guide.html](https://kotlinlang.org/docs/k2-compiler-migration-guide.html)

[12] Aho, A.V., Lam, M.S., Sethi, R., Ullman, J.D. *Compilers: Principles, Techniques, and Tools* (2nd ed.). Addison-Wesley, 2006. ISBN: 0-321-48681-1.

[13] Lieberman, H. *There's More to Menu Systems Than Meets the Screen.* In: SIGGRAPH '85, Computer Graphics, Vol. 19, No. 3, pp. 171-179, 1985.

[14] Neron, P., Tolmach, A., Visser, E., Wachsmuth, G. *A Theory of Name Resolution.* In: ESOP 2015, LNCS Vol. 9032, pp. 205-231. Springer, 2015. DOI: [10.1007/978-3-662-46669-8_9](https://link.springer.com/chapter/10.1007/978-3-662-46669-8_9)

[15] Konat, G., Kats, L., Wachsmuth, G., Visser, E. *Declarative Name Binding and Scope Rules.* In: SLE 2012, LNCS Vol. 7745, pp. 311-331. Springer, 2013. DOI: [10.1007/978-3-642-36089-3_18](https://link.springer.com/chapter/10.1007/978-3-642-36089-3_18)

[16] Ryder, B.G. *Constructing the Call Graph of a Program.* IEEE Transactions on Software Engineering, Vol. SE-5, No. 3, pp. 216-226, 1979. DOI: [10.1109/TSE.1979.234183](https://ieeexplore.ieee.org/document/1702621/)

[17] Grove, D., Chambers, C. *A Framework for Call Graph Construction Algorithms.* ACM TOPLAS, Vol. 23, No. 6, pp. 685-746, 2001. DOI: [10.1145/506315.506316](https://dl.acm.org/doi/10.1145/506315.506316)

[18] Tip, F., Palsberg, J. *Scalable Propagation-Based Call Graph Construction Algorithms.* In: OOPSLA 2000, pp. 281-293. ACM, 2000. DOI: [10.1145/353171.353190](https://dl.acm.org/doi/10.1145/353171.353190)

[19] Dean, J., Grove, D., Chambers, C. *Optimization of Object-Oriented Programs Using Static Class Hierarchy Analysis.* In: ECOOP'95, LNCS Vol. 952, pp. 77-101. Springer, 1995. DOI: [10.1007/3-540-49538-X_5](https://link.springer.com/chapter/10.1007/3-540-49538-X_5)

[20] Bushong, V., Das, D., Al Maruf, A., Cerny, T. *Using Static Analysis to Address Microservice Architecture Reconstruction.* In: ASE 2021, pp. 1199-1201. IEEE, 2021. DOI: [10.1109/ASE51524.2021.9678749](https://dl.acm.org/doi/10.1109/ASE51524.2021.9678749)

[21] Cerny, T., Abdelfattah, A.S., Yero, J. et al. *From Static Code Analysis to Visual Models of Microservice Architecture.* Cluster Computing, Springer, 2024. DOI: [10.1007/s10586-024-04394-7](https://link.springer.com/article/10.1007/s10586-024-04394-7)

[22] Abdelfattah, A.S., Cerny, T. *The Microservice Dependency Matrix.* In: ESOCC 2023, LNCS Vol. 14183, pp. 268-283. Springer, 2023. DOI: [10.1007/978-3-031-46235-1_19](https://link.springer.com/chapter/10.1007/978-3-031-46235-1_19)

[23] Lea, D. *A Java Fork/Join Framework.* In: ACM 2000 Conference on Java Grande, pp. 36-43. ACM, 2000. DOI: [10.1145/337449.337465](https://dl.acm.org/doi/10.1145/337449.337465)

[24] Blumofe, R.D., Leiserson, C.E. *Scheduling Multithreaded Computations by Work Stealing.* Journal of the ACM, Vol. 46, No. 5, pp. 720-748, 1999. DOI: [10.1145/324133.324234](https://dl.acm.org/doi/10.1145/324133.324234)

[25] Eugster, P.Th., Felber, P.A., Guerraoui, R., Kermarrec, A.-M. *The Many Faces of Publish/Subscribe.* ACM Computing Surveys, Vol. 35, No. 2, pp. 114-131, 2003. DOI: [10.1145/857076.857078](https://dl.acm.org/doi/10.1145/857076.857078)

[26] Dietrich, C., Rothberg, V., Furacker, L., Ziegler, A., Lohmann, D. *cHash: Detection of Redundant Compilations via AST Hashing.* In: USENIX ATC 2017, pp. 527-538. USENIX Association, 2017. **Best Paper Award.** URL: [usenix.org](https://www.usenix.org/conference/atc17/technical-sessions/presentation/dietrich)

[27] Smits, J., Konat, G., Visser, E. *Constructing Hybrid Incremental Compilers for Cross-Module Extensibility with an Internal Build System.* arXiv:2002.06183, 2020. URL: [arxiv.org](https://arxiv.org/abs/2002.06183)

[28] Neamtiu, I., Foster, J.S., Hicks, M. *Understanding Source Code Evolution Using Abstract Syntax Tree Matching.* In: MSR 2005. ACM, 2005. DOI: [10.1145/1083142.1083143](https://dl.acm.org/doi/abs/10.1145/1083142.1083143)

[29] Zhang, J., Wang, X., Zhang, H., Sun, H., Wang, K., Liu, X. *A Novel Neural Source Code Representation Based on Abstract Syntax Tree.* In: ICSE 2019. IEEE/ACM, 2019. DOI: [10.1109/ICSE.2019.00086](https://dl.acm.org/doi/10.1109/ICSE.2019.00086)

[30] Hindle, A., Barr, E.T., Gabel, M., Su, Z., Devanbu, P. *On the Naturalness of Software.* In: ICSE 2012. IEEE, 2012. URL: [people.inf.ethz.ch](https://people.inf.ethz.ch/suz/publications/natural.pdf)

[31] Allamanis, M., Barr, E.T., Devanbu, P., Sutton, C. *A Survey of Machine Learning for Big Code and Naturalness.* ACM Computing Surveys, Vol. 51, No. 4, Article 81, 2018. DOI: [10.1145/3212695](https://dl.acm.org/doi/10.1145/3212695)

[32] Ducasse, S., Pollet, D. *Software Architecture Reconstruction: A Process-Oriented Taxonomy.* IEEE TSE, Vol. 35, No. 4, pp. 573-591, 2009. DOI: [10.1109/TSE.2009.19](https://ieeexplore.ieee.org/document/4815276/)

[33] Ramler, R. et al. *Benefits and Drawbacks of Representing and Analyzing Source Code and Software Engineering Artifacts with Graph Databases.* In: SWQD 2019, LNBIP Vol. 338, pp. 125-143. Springer, 2019. DOI: [10.1007/978-3-030-05767-1_9](https://link.springer.com/chapter/10.1007/978-3-030-05767-1_9)

[34] Knuth, D.E. *The Art of Computer Programming, Volume 1: Fundamental Algorithms* (3rd ed.). Addison-Wesley, 1997. ISBN: 0-201-89683-4.

[35] *A Comprehensive Survey on Automatic Knowledge Graph Construction.* ACM Computing Surveys, 2023. DOI: [10.1145/3618295](https://dl.acm.org/doi/10.1145/3618295)

[36] Meyer, B. *Object-Oriented Software Construction* (2nd ed.). Prentice Hall, 1997. ISBN: 0-13-629155-4.
