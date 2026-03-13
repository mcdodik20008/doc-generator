# Модуль Retrieval-Augmented Generation (RAG)

## Оглавление

1. [Введение и теоретические основы](#1-введение-и-теоретические-основы)
2. [Архитектура модуля](#2-архитектура-модуля)
3. [Конечный автомат обработки запросов (FSM)](#3-конечный-автомат-обработки-запросов-fsm)
4. [Стадии FSM-пайплайна](#4-стадии-fsm-пайплайна)
5. [Модуль чанкинга (Chunking)](#5-модуль-чанкинга-chunking)
6. [Модуль векторных вложений (Embedding)](#6-модуль-векторных-вложений-embedding)
7. [Векторный поиск и приближённый поиск ближайших соседей](#7-векторный-поиск-и-приближённый-поиск-ближайших-соседей)
8. [Интеграция с графом знаний](#8-интеграция-с-графом-знаний)
9. [Расширение запросов и словарь синонимов](#9-расширение-запросов-и-словарь-синонимов)
10. [Ранжирование и фильтрация результатов](#10-ранжирование-и-фильтрация-результатов)
11. [Сборка контекста и формирование промпта](#11-сборка-контекста-и-формирование-промпта)
12. [LLM-клиенты и локальный инференс](#12-llm-клиенты-и-локальный-инференс)
13. [REST API и потоковая генерация (SSE)](#13-rest-api-и-потоковая-генерация-sse)
14. [Отказоустойчивость и каскадная деградация](#14-отказоустойчивость-и-каскадная-деградация)
15. [Модель данных](#15-модель-данных)
16. [Оценка качества ответов](#16-оценка-качества-ответов)
17. [Тестирование](#17-тестирование)
18. [Список литературы](#18-список-литературы)

---

## 1. Введение и теоретические основы

### 1.1 Парадигма Retrieval-Augmented Generation

Retrieval-Augmented Generation (RAG) — парадигма, впервые предложенная Lewis et al. [1] на конференции NeurIPS 2020, объединяющая параметрическую память (предобученная seq2seq модель) с непараметрической памятью (плотный векторный индекс, доступный через нейронный ретривер). Авторы доказали, что RAG-модели генерируют более специфичные, разнообразные и фактуально точные ответы по сравнению с чисто параметрическими базовыми линиями.

Gao et al. [2] в обзоре 2024 года классифицировали RAG-системы на три поколения:
- **Naive RAG**: простой цикл «получи → прочитай → сгенерируй»;
- **Advanced RAG**: с предобработкой запроса, ранжированием, расширением контекста;
- **Modular RAG**: с модульной архитектурой, позволяющей комбинировать компоненты.

Наша система реализует архитектуру **Advanced RAG** с элементами **Modular RAG**: конечный автомат (FSM) оркестрирует модульные шаги обработки, каждый из которых может быть заменён или отключён независимо. Singh et al. [3] описали архитектуру **Agentic RAG**, где автономные AI-агенты встроены в пайплайн — наш FSM-подход является упрощённой детерминистической реализацией той же идеи.

### 1.2 RAG для исходного кода

Применение RAG к исходному коду имеет специфику:
- Код имеет формальную структуру (AST), которую можно использовать для чанкинга [4];
- Семантический поиск по коду отличается от поиска по естественному языку [5, 6];
- Контекст проекта (граф зависимостей, иерархия классов) критичен для генерации [7].

Tao et al. [8] провели обзор Retrieval-Augmented Code Generation (RACG), покрывающий стратегии генерации, модальности извлечения и архитектуры моделей на уровне репозиториев. Lomshakov et al. [7] показали, что использование контекста проекта значительно улучшает качество саммаризации кода — именно этот принцип лежит в основе нашей системы, извлекающей контекст из графа знаний.

### 1.3 Теоретический фундамент

Архитектура системы базируется на следующих фундаментальных работах:

- **Transformer** (Vaswani et al. [9]): архитектура, лежащая в основе всех используемых LLM и embedding-моделей;
- **In-Context Learning** (Brown et al. [10]): способность LLM использовать контекст промпта для генерации, без дообучения;
- **Dense Passage Retrieval** (Karpukhin et al. [11]): двухэнкодерный фреймворк для плотного поиска, превзошедший BM25 на 9–19%;
- **Sentence-BERT** (Reimers & Gurevych [12]): bi-encoder архитектура для генерации семантических эмбеддингов предложений;
- **Naturalness of Software** (Hindle et al. [13]): гипотеза о «естественности» кода — код повторяем и может быть смоделирован статистическими языковыми моделями.

---

## 2. Архитектура модуля

### 2.1 Модульная структура

RAG-пайплайн распределён по четырём контекстным модулям:

```
contexts/
├── rag/                    # Ядро RAG-пайплайна
│   ├── api/                # Интерфейсы, DTO, типы шагов
│   │   ├── RagService.kt
│   │   ├── QueryProcessingContext.kt
│   │   ├── QueryMetadataKeys.kt
│   │   ├── ProcessingStepType.kt
│   │   └── ProcessingStepStatus.kt
│   └── impl/               # Реализации
│       ├── RagServiceImpl.kt
│       ├── GraphRequestProcessor.kt    # FSM-движок
│       ├── ResultFilterService.kt      # Ранжирование
│       ├── CustomPgVectorStoreConfig.kt
│       └── steps/           # 8 шагов FSM
│           ├── NormalizationStep.kt
│           ├── ExtractionStep.kt
│           ├── ExactSearchStep.kt
│           ├── GraphExpansionStep.kt
│           ├── RewritingStep.kt
│           ├── ExpansionStep.kt
│           ├── VectorSearchStep.kt
│           └── RerankingStep.kt
├── chunking/               # Стратегии разбиения на чанки
│   ├── api/
│   │   ├── ChunkStrategy.kt
│   │   └── model/plan/     # ChunkPlan, PipelinePlan
│   └── impl/
│       └── PerNodeChunkStrategy.kt
├── embedding/              # Генерация и поиск по эмбеддингам
│   ├── api/
│   │   ├── EmbeddingSearchService.kt
│   │   └── EmbeddingStoreService.kt
│   └── impl/
│       ├── EmbeddingSearchServiceImpl.kt
│       └── EmbeddingStoreServiceImpl.kt
└── ai/                     # Обёртки LLM-клиентов
    ├── embedding/
    │   ├── EmbeddingClient.kt
    │   └── ProxyEmbeddingClient.kt
    ├── config/
    │   └── EmbeddingsConfig.kt
    └── props/
        └── AiClientsProperties.kt
```

### 2.2 Поток данных

```
Пользовательский запрос
        │
        ▼
┌─────────────────────────────┐
│   RagController (REST API)  │  POST /api/rag/ask
└────────────┬────────────────┘
             │
             ▼
┌─────────────────────────────┐
│     RagServiceImpl          │  Оркестрация + сборка контекста
│  ┌──────────────────────┐   │
│  │ GraphRequestProcessor│   │  FSM-пайплайн (8 шагов)
│  │  ┌─NORMALIZATION──┐  │   │
│  │  │ EXTRACTION      │  │   │
│  │  │ EXACT_SEARCH    │  │   │ ← NodeRepository (БД)
│  │  │ GRAPH_EXPANSION │  │   │ ← EdgeRepository (БД)
│  │  │ REWRITING       │  │   │ ← LLM (Ollama)
│  │  │ EXPANSION       │  │   │ ← SynonymDictionary + Embeddings
│  │  │ VECTOR_SEARCH   │  │   │ ← PgVectorStore (pgvector)
│  │  │ RERANKING       │  │   │
│  │  └─COMPLETED/FAILED┘  │   │
│  └──────────────────────┘   │
│                             │
│  Сборка контекста + промпт  │
│  Вызов LLM → Ответ         │  ← Ollama (qwen2.5-coder)
└─────────────────────────────┘
             │
             ▼
       RagResponse
  (answer, sources, metadata)
```

---

## 3. Конечный автомат обработки запросов (FSM)

### 3.1 Теоретическое обоснование

Применение конечных автоматов (Finite State Machine, FSM) для управления NLP-пайплайнами имеет как классические [14], так и современные обоснования. Wu et al. [15] предложили **StateFlow** — фреймворк для оркестрации LLM-задач через конечные автоматы, демонстрирующий на 13–28% более высокие показатели успешности по сравнению с ReAct при трёх-пятикратном снижении затрат. Xu et al. [16] разработали **DFA-RAG** — детерминистический конечный автомат, встроенный в LLM для управления маршрутизацией ответов.

В нашей системе FSM обеспечивает:
- **Детерминизм**: определённые переходы между состояниями гарантируют предсказуемость;
- **Наблюдаемость**: каждый переход логируется с таймингами;
- **Отказоустойчивость**: ошибка на любом шаге имеет определённый фоллбэк;
- **Расширяемость**: новые шаги добавляются как Spring-компоненты без модификации ядра.

### 3.2 Диаграмма переходов

```
                    ┌──────────────┐
                    │ NORMALIZATION │
                    └──────┬───────┘
                     SUCCESS│
                    ┌──────▼───────┐
                    │  EXTRACTION  │
                    └──┬───────┬───┘
                 FOUND │       │ NOT_FOUND
              ┌────────▼──┐  ┌─▼──────────┐
              │EXACT_SEARCH│  │  REWRITING  │
              └──┬─────┬──┘  └──────┬──────┘
          HAS_DATA│    │NO_DATA   SUCCESS│
     ┌────────────▼┐   │    ┌──────▼──────┐
     │   GRAPH     │   └────►  EXPANSION  │
     │  EXPANSION  │        └──────┬──────┘
     └──┬──────┬───┘         SUCCESS│
  SUCCESS│     │NO_NODES  ┌────────▼────────┐
         │  ┌──▼──────────│  VECTOR_SEARCH  │
         │  │             └────────┬────────┘
         │  │                SUCCESS│
    ┌────▼──▼────┐                 │
    │  RERANKING ◄─────────────────┘
    └──┬──────┬──┘
 SUCCESS│     │EMPTY
  ┌─────▼──┐ ┌▼──────┐
  │COMPLETED│ │FAILED │
  └────────┘ └───────┘
```

### 3.3 Реализация: GraphRequestProcessor

`GraphRequestProcessor` — движок FSM, реализующий паттерн **State Machine** с реестром шагов:

```kotlin
class GraphRequestProcessor(
    private val steps: Map<ProcessingStepType, QueryStep>  // Реестр шагов
) {
    fun process(context: QueryProcessingContext): QueryProcessingContext {
        var currentStep = ProcessingStepType.NORMALIZATION
        val visitedSteps = mutableSetOf<ProcessingStepType>()
        var iterations = 0

        while (currentStep !in setOf(COMPLETED, FAILED) && iterations < MAX_ITERATIONS) {
            // Защита от циклов
            if (currentStep in visitedSteps) {
                return context.fail("Cycle detected at $currentStep")
            }
            visitedSteps.add(currentStep)

            val step = steps[currentStep] ?: return context.fail("No handler")

            // Выполнение с таймаутом
            val result = CompletableFuture
                .supplyAsync { step.execute(context) }
                .orTimeout(stepTimeoutSeconds, TimeUnit.SECONDS)
                .get()

            // Переход по ключу результата
            currentStep = step.transitions[result.transitionKey]
                ?: return context.fail("Unknown transition: ${result.transitionKey}")

            iterations++
        }
        return context
    }
}
```

### 3.4 Интерфейс шага FSM

```kotlin
interface QueryStep {
    val stepType: ProcessingStepType
    val transitions: Map<String, ProcessingStepType>  // transitionKey → nextStep
    fun execute(context: QueryProcessingContext): StepResult
}

data class StepResult(
    val transitionKey: String,    // "SUCCESS", "FOUND", "HAS_DATA", etc.
    val message: String = ""
)
```

### 3.5 Защитные механизмы

| Механизм | Описание | Конфигурация |
|----------|----------|--------------|
| **Детекция циклов** | Множество `visitedSteps` предотвращает повторный визит | Встроен |
| **Таймаут шага** | `CompletableFuture.orTimeout()` на каждый шаг | `step-timeout-seconds: 30` |
| **Общий таймаут** | Ограничение на весь пайплайн | `processing-timeout-seconds: 45` |
| **Лимит итераций** | Жёсткий предел в 20 итераций | `MAX_ITERATIONS = 20` |
| **Детекция медленных шагов** | Предупреждение при превышении порога | `slow-step-threshold-ms: 10000` |

---

## 4. Стадии FSM-пайплайна

### 4.1 NORMALIZATION — Нормализация запроса

**Назначение**: стандартизация формата запроса для повышения качества последующих шагов.

**Алгоритм**:
```
function normalize(query):
    result := regex_replace(query, '\s+', ' ')  // Коллапс пробелов
    result := trim(result)                       // Удаление пробелов по краям
    result := regex_replace(result, '[?!.,;:]+$', '')  // Удаление терминальной пунктуации
    return result
```

**Свойства**: идемпотентная операция, O(n) по длине запроса.

**Пример**: `"What does  process  method do??"` → `"What does process method do"`

**Переходы**: всегда `SUCCESS → EXTRACTION`

### 4.2 EXTRACTION — Извлечение сущностей

**Назначение**: идентификация имён классов и методов в запросе для точного поиска.

**Двухпутевой алгоритм**:

```
function extract(query):
    // Путь 1: LLM (основной)
    try:
        response := fastExtractionChatClient.call(
            prompt = "Извлеки имена классов и методов из запроса.
                     Верни JSON: {className: ..., methodName: ...}",
            query = query
        )
        result := parseJson(response)
        return result

    // Путь 2: Regex (фоллбэк)
    catch (timeout | error):
        className := regex_match(query, '(?:класс|class)\s+(\w+)')
        methodName := regex_match(query, '(?:метод|method)\s+(\w+)')
        return {className, methodName}
```

**Переходы**:
- `FOUND` (className или methodName не null) → `EXACT_SEARCH`
- `NOT_FOUND` (оба null) → `REWRITING`

### 4.3 EXACT_SEARCH — Точный поиск по базе данных

**Назначение**: поиск узлов графа знаний по извлечённым именам.

**Алгоритм**:
```
function exactSearch(className, methodName, applications):
    results := ∅
    for each app in applications:
        if className AND methodName:
            results += nodeRepo.findByClassAndMethod(app.id, className, methodName)
        elif className:
            results += nodeRepo.findByClassName(app.id, className)
        elif methodName:
            results += nodeRepo.findByMethodName(app.id, methodName)

    return deduplicate(results, by=node.id)
```

**Переходы**:
- `HAS_DATA` (|results| >= 1) → `GRAPH_EXPANSION`
- `NO_DATA` (|results| = 0) → `REWRITING`

### 4.4 GRAPH_EXPANSION — Расширение через граф зависимостей

**Назначение**: обогащение контекста через обход графа зависимостей кода.

**Алгоритм BFS (Breadth-First Search)** с ограниченным радиусом:

```
function graphExpansion(seedNodes, maxRadius=2):
    visited := set(seedNodes)
    frontier := set(seedNodes)

    for radius in 1..maxRadius:
        edges := edgeRepo.findByNodesAndKinds(
            nodeIds = frontier,
            kinds = [CALLS_CODE, DEPENDS_ON, IMPLEMENTS, READS, WRITES]
        )
        newIds := extractNodeIds(edges) \ visited
        visited := visited ∪ newIds
        frontier := newIds

    neighborNodes := visited \ seedNodes

    // Формирование текстового описания связей
    graphRelationsText := ""
    for each edge in edges:
        graphRelationsText += formatEdge(edge)
        // "[Class A] calls [Method B] (Type: CALLS_CODE)"

    return neighborNodes, graphRelationsText
```

**Допустимые типы рёбер**: `CALLS_CODE`, `DEPENDS_ON`, `IMPLEMENTS`, `READS`, `WRITES` — выбраны как наиболее информативные для понимания контекста метода.

**Переходы**: `SUCCESS → RERANKING` (или `NO_NODES → VECTOR_SEARCH`)

### 4.5 REWRITING — Переформулирование запроса

**Назначение**: улучшение запроса для векторного поиска через LLM.

**Алгоритм**:
```
function rewrite(query):
    if context.metadata[REWRITTEN] == true:
        return SKIP  // Идемпотентность

    prompt := "Переформулируй запрос для лучшего поиска технической документации.
               ОБЯЗАТЕЛЬНО сохрани все точные названия классов, методов
               без изменений — CamelCase, цифры, всё как было."

    rewrittenQuery := ragChatClient.call(prompt, query)
    context.currentQuery := rewrittenQuery
    context.metadata[REWRITTEN_QUERY] := rewrittenQuery
    return SUCCESS
```

**Переходы**: `SUCCESS → EXPANSION`

### 4.6 EXPANSION — Семантическое расширение запроса

**Назначение**: обогащение запроса синонимами и связанными терминами через двойную валидацию эмбеддингов.

**Трёхэтапный алгоритм** (подробно описан в разделе 9):

```
function expand(query):
    // Этап 1: Генерация эмбеддинга запроса
    E_q := embeddingClient.embed(query)  // → FloatArray[1024]

    // Этап 2: Поиск кандидатов по term_embedding
    topK := synonymRepo.findTopByTermEmbedding(E_q, k=3)

    // Этап 3: Валидация по desc_embedding
    validated := filter(topK, t => cosine(E_q, t.descEmbedding) > 0.7)

    // Формирование расширенного запроса
    final := topK ∩ validated
    expandedQuery := query + " (Контекст: " +
        join(final, t => t.term + ": " + t.description, "; ") + ")"

    context.currentQuery := expandedQuery
    return SUCCESS
```

**Переходы**: `SUCCESS → VECTOR_SEARCH`

### 4.7 VECTOR_SEARCH — Векторный поиск

**Назначение**: поиск семантически релевантных чанков через приближённый поиск ближайших соседей.

**Алгоритм двойного запроса**:
```
function vectorSearch(originalQuery, rewrittenQuery):
    results := embeddingSearchService.searchByText(originalQuery, topK=5)

    if rewrittenQuery != null AND rewrittenQuery != originalQuery:
        results += embeddingSearchService.searchByText(rewrittenQuery, topK=5)

    mergedResults := results
        .distinctBy(id)
        .sortByDescending(similarity)

    return mergedResults
```

**Обоснование двойного запроса**: исходный запрос сохраняет точные технические термины пользователя, а переписанный расширяет семантическое покрытие. Объединение обеспечивает высокий recall без потери precision.

**Переходы**: `SUCCESS → RERANKING`

### 4.8 RERANKING — Ранжирование и фильтрация

**Назначение**: фильтрация результатов векторного поиска с использованием извлечённых имён классов/методов.

Подробный алгоритм описан в разделе 10.

**Переходы**:
- `SUCCESS` (есть результаты ИЛИ есть точные узлы ИЛИ есть текст графа) → `COMPLETED`
- `EMPTY` (нет ничего) → `FAILED`

---

## 5. Модуль чанкинга (Chunking)

### 5.1 Теоретические основы

Стратегия разбиения документов на чанки (chunking) критически влияет на качество RAG-систем. Wu et al. [4] предложили **cAST** — метод структурного чанкинга на основе AST, который рекурсивно разбивает крупные узлы AST на меньшие фрагменты и объединяет соседние узлы с учётом ограничений размера, повысив Recall@5 на 4.3 пункта.

Liu et al. [17] эмпирически показали, что семантический чанкинг не всегда оправдывает вычислительные затраты — фиксированный чанкинг часто показывает сопоставимые результаты. Merola & Singh [18] провели систематическую оценку стратегий чанкинга для RAG: фиксированный, рекурсивный, семантический и контекстно-зависимый.

### 5.2 Реализация: PerNodeChunkStrategy

Наша система использует стратегию **«один узел — один чанк»** (per-node chunking), которая обеспечивает:
- **Структурную целостность**: каждый чанк соответствует семантически осмысленной единице кода (класс, метод, поле);
- **Точное связывание**: чанк однозначно привязан к узлу графа знаний через `nodeId`;
- **Идемпотентность**: детерминистический ID чанка `{nodeId}:{source}:{kind}` гарантирует воспроизводимость.

**Интерфейс стратегии** (паттерн Strategy [19]):

```kotlin
interface ChunkStrategy {
    fun plan(nodes: List<Node>, application: Application): List<ChunkPlan>
}
```

### 5.3 Алгоритм принятия решений

```
function planChunks(node):
    if node.signature != null OR node.docComment != null:
        // Документационный чанк
        return ChunkPlan(
            id = "${node.id}:doc:tech",
            source = "doc",
            kind = "tech",
            content = formatDocContent(node.signature, node.docComment),
            pipeline = ["render-doc", "embed", "link-edges"]
        )
    else:
        // Чанк исходного кода
        return ChunkPlan(
            id = "${node.id}:code:snippet",
            source = "code",
            kind = "snippet",
            content = node.sourceCode,
            pipeline = ["extract-snippet", "summarize", "embed", "link-edges"]
        )
```

### 5.4 Структура ChunkPlan

```kotlin
data class ChunkPlan(
    val id: String,                      // Детерминистический: "{nodeId}:{source}:{kind}"
    val nodeId: Long,                    // FK → Node
    val source: String,                  // "doc" | "code"
    val kind: String,                    // "tech" | "snippet"
    val lang: String?,                   // "kotlin", "java", "ru"
    val spanLines: IntRange?,            // Строки в исходном файле
    val title: String?,                  // FQN узла
    val sectionPath: List<String>,       // ["package", "ClassName", "methodName"]
    val relations: List<RelationHint>,   // Исходящие CALLS рёбра
    val pipeline: PipelinePlan,          // Этапы обработки
    val node: Node                       // Полная ссылка на узел
)
```

### 5.5 Пайплайн обработки чанка

| Этап | Назначение | Применяется к |
|------|-----------|---------------|
| `render-doc` | Форматирование документационных строк | DOC-чанки |
| `extract-snippet` | Извлечение фрагмента кода из файла | CODE-чанки |
| `summarize` | Генерация краткой аннотации через LLM | CODE-чанки |
| `embed` | Генерация вектора (1024 dims, BGE-M3) | Все чанки |
| `link-edges` | Создание метаданных связей | Все чанки |

### 5.6 Система приоритетов

```kotlin
fun priorityFor(node: Node): Int = when (node.kind) {
    ENDPOINT, METHOD -> 10  // Высокий: точки входа и методы
    CLASS            -> 5   // Средний: классы
    else             -> 0   // Низкий: остальные
}
```

Приоритеты используются при упорядочивании чанков для эмбеддинга, обеспечивая первоочередную обработку наиболее часто запрашиваемых элементов.

---

## 6. Модуль векторных вложений (Embedding)

### 6.1 Модель BGE-M3

Система использует модель **BGE-M3** (BAAI General Embedding — Multi-Lingual, Multi-Functionality, Multi-Granularity), описанную Chen et al. [20]. BGE-M3 поддерживает:
- **100+ языков** (включая русский, актуальный для комментариев в коде);
- **Три функциональности**: плотный (dense), мультивекторный (multi-vector) и разреженный (sparse) поиск;
- **Контекст до 8192 токенов**: достаточный для больших методов и классов;
- **Самодистилляция** (self-knowledge distillation): оценки из разных функциональностей служат сигналами учителя.

Наша система использует **плотный (dense)** режим, генерируя вектора размерности **1024**.

### 6.2 Архитектура Bi-Encoder

BGE-M3 базируется на архитектуре **bi-encoder**, формализованной Reimers & Gurevych [12] в Sentence-BERT. Ключевое свойство: документы и запросы кодируются **независимо**, что позволяет предвычислять эмбеддинги документов оффлайн и выполнять быстрый поиск через скалярное произведение или косинусное расстояние.

### 6.3 Интерфейс и реализация

```kotlin
interface EmbeddingClient {
    val modelName: String  // "bge-m3"
    val dim: Int           // 1024
    fun embed(text: String): FloatArray
}

class ProxyEmbeddingClient(
    private val embeddingModel: EmbeddingModel  // Spring AI
) : EmbeddingClient {
    override fun embed(text: String): FloatArray {
        val response = embeddingModel.embed(text)
        require(response.size == dim) { "Dimension mismatch" }
        return response
    }
}
```

### 6.4 Конфигурация через Ollama

```yaml
spring:
  ai:
    ollama:
      base-url: http://${OLLAMA_HOSTNAME:localhost}:11434
      embedding:
        model: ${OLLAMA_EMBEDDING_MODEL:bge-m3}
        dim: 1024
        options:
          num-ctx: 8192  # Окно контекста
```

Ollama обеспечивает **локальный инференс** без внешних API-вызовов, что гарантирует конфиденциальность кода и низкую задержку. Desislavov et al. [21] сравнили фреймворки локального инференса и отметили, что Ollama приоритизирует удобство разработки (developer ergonomics) при приемлемой производительности.

---

## 7. Векторный поиск и приближённый поиск ближайших соседей

### 7.1 Математическое обоснование

Vectorный поиск основан на **модели векторного пространства** (Vector Space Model), формализованной Turney & Pantel [22]: документы и запросы представляются точками в многомерном пространстве, а релевантность определяется расстоянием между ними.

**Косинусное сходство** между векторами **a** и **b** определяется как:

```
cos(a, b) = (a · b) / (||a|| × ||b||) ∈ [-1, 1]
```

Для нормализованных эмбеддингов (как в BGE-M3) косинусное сходство эквивалентно скалярному произведению.

### 7.2 Алгоритм HNSW

Для приближённого поиска ближайших соседей (Approximate Nearest Neighbor, ANN) используется алгоритм **HNSW** (Hierarchical Navigable Small World), предложенный Malkov & Yashunin [23] и опубликованный в IEEE TPAMI. HNSW строит многослойную навигабельную структуру:

1. **Многослойная иерархия**: каждый элемент присваивается случайному слою с экспоненциально убывающей вероятностью;
2. **Навигация**: поиск начинается с верхнего слоя (крупнозернистый) и спускается к нижнему (точный);
3. **Жадная маршрутизация**: на каждом слое выполняется жадный поиск ближайшего соседа;
4. **Логарифмическая сложность**: O(log n) для поиска.

**Преимущества перед IVFFlat** (Jegou et al. [24]):
- Не требует предварительного кластеризации данных;
- Лучшая производительность при высоком recall;
- Инкрементальное добавление новых векторов без пересоздания индекса.

### 7.3 Конфигурация pgvector

```kotlin
@Bean
fun customPgVectorStore(
    jdbcTemplate: JdbcTemplate,
    embeddingModel: EmbeddingModel
): PgVectorStore {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .schemaName("doc_generator")
        .vectorTableName("chunk")
        .distanceType(PgDistanceType.COSINE_DISTANCE)
        .initializeSchema(false)   // Управление через Liquibase
        .dimensions(1024)
        .build()
}
```

**pgvector** [25] — расширение PostgreSQL для хранения и поиска векторов. Оно реализует алгоритмы HNSW и IVFFlat, обеспечивая нативную интеграцию с PostgreSQL (транзакции, ACID, SQL-запросы).

### 7.4 Реализация поиска

```kotlin
override fun searchByText(query: String, topK: Int = 10): List<SearchResult> {
    val searchRequest = SearchRequest.builder()
        .query(query)
        .topK(topK)
        .build()

    val results = vectorStore.similaritySearch(searchRequest)

    return results.map { doc ->
        SearchResult(
            id = doc.id,
            content = doc.text,
            metadata = doc.metadata,
            similarity = doc.score   // 1 - cosine_distance ∈ [0, 1]
        )
    }
}
```

Процесс: текст запроса → эмбеддинг (1024 floats через Ollama) → HNSW-поиск в pgvector → top-K результатов, отсортированных по убыванию сходства.

---

## 8. Интеграция с графом знаний

### 8.1 Мотивация

RAG-системы, опирающиеся только на векторный поиск, теряют **структурный контекст**: зависимости между классами, цепочки вызовов, иерархии наследования. Наша система решает эту проблему, комбинируя векторный поиск с **обходом графа знаний** (Knowledge Graph traversal).

Abdelaziz et al. [26] продемонстрировали эффективность графов знаний кода для задач извлечения и генерации. Knowledge Graph Based Repository-Level Code Generation [27] показала, что графовый контекст значительно улучшает генерацию кода на уровне репозитория.

### 8.2 GraphExpansionStep: BFS-обход

Алгоритм поиска в ширину (BFS) с ограниченным радиусом позволяет собрать контекст из ближайшего окружения найденных узлов:

```
function graphExpansion(seedNodes, maxRadius=2, allowedEdgeKinds):
    visited := seedNodes
    frontier := seedNodes

    for radius := 1 to maxRadius:
        edges := edgeRepo.findBySourceOrTarget(
            nodeIds = frontier.map(id),
            kinds = allowedEdgeKinds
        )

        newNodeIds := {}
        for each edge in edges:
            newNodeIds += edge.src.id
            newNodeIds += edge.dst.id

        nextFrontier := newNodeIds \ visited
        visited += nextFrontier
        frontier := nextFrontier

    neighbors := visited \ seedNodes
    return neighbors, edges
```

### 8.3 Формирование текстового описания графа

Рёбра графа преобразуются в человекочитаемый текст:

```
function formatEdge(edge):
    srcLabel := edge.src.name ?: edge.src.fqn ?: "Node#${edge.src.id}"
    dstLabel := edge.dst.name ?: edge.dst.fqn ?: "Node#${edge.dst.id}"
    return "[${srcLabel}] ${edge.kind.verb} [${dstLabel}] (Type: ${edge.kind})"
```

Пример: `[UserService] calls [UserRepository.findById] (Type: CALLS_CODE)`

Текст обрезается до `maxGraphRelationsChars` (5000 символов по умолчанию).

---

## 9. Расширение запросов и словарь синонимов

### 9.1 Теоретическое обоснование

Гибридный поиск (hybrid search), комбинирующий ключевые слова (BM25) и векторный поиск, показывает лучшие результаты, чем каждый метод по отдельности. Sawarkar et al. [28] продемонстрировали, что гибридный поиск достигает NDCG@10 = 0.67, превосходя моноT5-3B.

Наша система реализует форму гибридного поиска через **семантическое расширение запроса**: вместо прямого комбинирования BM25 и vector search, запрос обогащается синонимами и контекстными терминами, что позволяет векторному поиску покрывать более широкое семантическое поле.

### 9.2 Двойная валидация эмбеддингов

**Структура словаря синонимов**:

| Поле | Тип | Описание |
|------|-----|----------|
| `id` | BIGINT | Первичный ключ |
| `term` | VARCHAR | Термин (например, «authentication») |
| `description` | TEXT | Описание термина |
| `term_embedding` | VECTOR(1024) | Эмбеддинг термина |
| `desc_embedding` | VECTOR(1024) | Эмбеддинг описания |
| `source_node_id` | BIGINT | FK → Node |

**Алгоритм двойной валидации**:

```
function findRelevantSynonyms(query, k=3, threshold=0.7):
    E_q := embeddingClient.embed(query)

    // Этап 1: Быстрый поиск по term_embedding (высокий recall)
    candidates := synonymRepo.findTopByTermEmbedding(E_q, k)

    // Этап 2: Валидация по desc_embedding (высокая precision)
    validated := [c for c in candidates
                  if cosine(E_q, c.descEmbedding) > threshold]

    // Этап 3: Пересечение гарантирует оба критерия
    return candidates ∩ validated
```

**Обоснование**: два независимых эмбеддинга (term vs description) обеспечивают робастность. Термин `«auth»` может быть поверхностно близок к `«author»`, но валидация по описанию (`«процесс аутентификации пользователя»`) отсеет ложные совпадения.

---

## 10. Ранжирование и фильтрация результатов

### 10.1 Теоретические основы

Khattab & Zaharia [29] предложили **ColBERT** — архитектуру с поздним взаимодействием (late interaction), обеспечивающую на два порядка более быструю работу, чем cross-encoder, при сопоставимой эффективности. В нашей системе используется более лёгкий подход — **двухуровневая ключевая фильтрация**, оптимизированная для задачи поиска по коду.

### 10.2 Алгоритм двухуровневой фильтрации

**Уровень 1: Точное совпадение (Exact Matching)**:

```
function exactMatch(content, keyword):
    pattern := compile('\b' + escapeRegex(keyword) + '\b', CASE_INSENSITIVE)
    return pattern.matcher(content).find()
```

Пример: `"UserService"` совпадёт с `"class UserService"`, но **не** с `"UserServiceHelper"`.

**Уровень 2: Нечёткое совпадение (Fuzzy Matching, фоллбэк)**:

```
function fuzzyMatch(content, keyword):
    // Разбиение CamelCase
    parts := splitCamelCase(keyword)
    // "UserService" → ["user", "service"]
    // "Step15Processor" → ["step", "15", "processor"]

    significantParts := parts.filter(p => p.length > 2)

    if significantParts.size > 1:
        // Композитное слово: ВСЕ значимые части должны присутствовать
        return significantParts.all(p => content.containsIgnoreCase(p))
    else:
        // Простое слово: подстрочный поиск
        return content.containsIgnoreCase(keyword)
```

**Логика выбора**:
```
function filterResults(results, className, methodName):
    keywords := [className, methodName].filterNotNull().filter(k => k.length > 2)

    if keywords.isEmpty():
        return results  // Нет фильтрации

    exactMatches := results.filter(r => keywords.any(k => exactMatch(r.content, k)))

    if exactMatches.isNotEmpty():
        return exactMatches

    fuzzyMatches := results.filter(r => keywords.any(k => fuzzyMatch(r.content, k)))

    if fuzzyMatches.isNotEmpty():
        return fuzzyMatches

    return results  // Без фильтрации как последний фоллбэк
```

---

## 11. Сборка контекста и формирование промпта

### 11.1 Проблема «потери в середине»

Liu et al. [30] обнаружили эффект **«Lost in the Middle»**: производительность LLM деградирует, когда релевантная информация находится в середине контекста; лучшие результаты достигаются, когда информация расположена в начале или конце. Это критично для проектирования промпта RAG-системы.

### 11.2 Алгоритм сборки контекста

Контекст собирается из четырёх источников и структурируется для оптимального восприятия LLM:

```
function buildContext(processingResult):
    context := StringBuilder()

    // 1. Точно найденные узлы (НАЧАЛО — наивысшая релевантность)
    exactNodes := processingResult.metadata[EXACT_NODES]
    if exactNodes:
        context += "=== ТОЧНО НАЙДЕННЫЕ УЗЛЫ ===\n"
        for each node in exactNodes.take(maxExactNodes=5):
            context += formatNode(node)
            // FQN, kind, name, signature, sourceCode (до 3000 символов), docComment

    // 2. Соседние узлы (РАСШИРЕННЫЙ контекст)
    neighbors := processingResult.metadata[NEIGHBOR_NODES]
    if neighbors:
        context += "=== СОСЕДНИЕ УЗЛЫ ===\n"
        for each node in neighbors.take(maxNeighborNodes=10):
            context += formatNode(node)

    // 3. Связи в графе (СТРУКТУРНЫЙ контекст)
    graphText := processingResult.metadata[GRAPH_RELATIONS_TEXT]
    if graphText:
        context += "=== СВЯЗИ В ГРАФЕ КОДА ===\n"
        context += graphText.take(maxGraphRelationsChars=5000)

    // 4. Результаты векторного поиска (СЕМАНТИЧЕСКИЙ контекст)
    chunks := processingResult.metadata[FILTERED_CHUNKS] ?: metadata[CHUNKS]
    if chunks:
        context += "=== РЕЗУЛЬТАТЫ ВЕКТОРНОГО ПОИСКА ===\n"
        for each chunk in chunks.distinctBy(id).take(5):
            context += "[${chunk.id}] ${chunk.content}\n"

    // 5. Обрезка общего контекста
    return context.take(maxContextChars=30000)
```

### 11.3 Структура промпта

```
Системный промпт:
    "Ты — умный ассистент разработчика. Отвечай на вопросы о коде,
     используя предоставленный контекст. Если в контексте нет ответа,
     честно скажи об этом."

Контекст:
    [Собранный контекст из 4 источников]

Вопрос:
    {originalQuery}
```

**Проектное решение**: вопрос пользователя размещается **в конце** промпта (а не в середине), что соответствует рекомендациям [30] для оптимального внимания LLM к запросу.

### 11.4 Ограничения контекста

| Параметр | Значение | Назначение |
|----------|----------|------------|
| `max-exact-nodes` | 5 | Максимум точных узлов |
| `max-neighbor-nodes` | 10 | Максимум соседних узлов |
| `max-node-code-chars` | 3,000 | Исходный код на узел |
| `max-graph-relations-chars` | 5,000 | Текст связей |
| `max-context-chars` | 30,000 | Общий лимит контекста |

---

## 12. LLM-клиенты и локальный инференс

### 12.1 Двухмодельная архитектура

Система использует **две специализированные LLM-модели**:

| Модель | Роль | Конфигурация |
|--------|------|-------------|
| **Coder** (qwen2.5-coder:14b) | Техническое объяснение кода | temperature=0.1, top_p=0.9, seed=42 |
| **Talker** (qwen2.5:14b-instruct) | Переписывание в человекочитаемый формат | temperature=0.3, top_p=0.9, seed=42 |

**Системный промпт Coder**:
```
Ты — строгий инженер-кодер. Объясняй код без домыслов,
пунктами, лаконично. Структура: Purpose; Inputs; Outputs;...
```

**Системный промпт Talker**:
```
Ты — технический писатель. Перепиши пояснение простой
человеческой речью (3–6 предложений)...
```

### 12.2 In-Context Learning

Подход основан на **In-Context Learning** (ICL), впервые продемонстрированном Brown et al. [10] в GPT-3. Dong et al. [31] провели обзор ICL, покрывающий стратегии обучения, дизайн промптов и анализ эффективности. Ключевой принцип: LLM использует контекст промпта для генерации ответа **без дообучения**, что позволяет динамически адаптировать систему к разным кодовым базам.

### 12.3 Локальный инференс через Ollama

Kachris [32] показал, что квантизированные LLM (через Ollama) снижают задержку инференса до 69%. Локальный инференс обеспечивает:
- **Конфиденциальность**: код не покидает периметр организации;
- **Низкую задержку**: отсутствие сетевых задержек;
- **Предсказуемость**: фиксированный `seed=42` обеспечивает воспроизводимость.

### 12.4 Конфигурация LLM-клиентов

```yaml
spring:
  ai:
    clients:
      coder:
        model: ${CODER_MODEL:qwen2.5-coder:14b}
        temperature: 0.1     # Минимальная стохастичность
        top-p: 0.9           # Nucleus sampling
        seed: 42             # Воспроизводимость
      talker:
        model: ${TALKER_MODEL:qwen2.5:14b-instruct}
        temperature: 0.3     # Умеренная вариативность
        top-p: 0.9
        seed: 42
```

---

## 13. REST API и потоковая генерация (SSE)

### 13.1 Синхронный эндпоинт

```
POST /api/rag/ask
Content-Type: application/json

{
    "query": "Как работает метод processPayment?",
    "sessionId": "user-123",
    "applicationId": 1
}

→ 200 OK
{
    "answer": "Метод processPayment выполняет...",
    "sources": [
        { "id": "42:doc:tech", "content": "...", "similarity": 0.87 }
    ],
    "metadata": {
        "originalQuery": "...",
        "rewrittenQuery": "...",
        "processingSteps": [...]
    }
}
```

Rate limit: 30 запросов / 60 секунд. Таймаут: 60 секунд.

### 13.2 Потоковый эндпоинт (Server-Sent Events)

```
POST /api/rag/ask/stream
Content-Type: application/json
Accept: text/event-stream

→ 200 OK (text/event-stream)

event: sources
data: [{"id": "42:doc:tech", "content": "...", "similarity": 0.87}]

event: metadata
data: {"originalQuery": "...", "processingSteps": [...]}

event: token
data: "Метод"

event: token
data: " processPayment"

event: token
data: " выполняет"

...

event: done
data: ""
```

SSE обеспечивает **прогрессивный рендеринг** ответа в UI: пользователь видит источники и метаданные сразу, а текст ответа появляется посимвольно по мере генерации LLM.

### 13.3 Эндпоинт с валидацией

```
POST /api/rag/ask-with-val

→ 200 OK
{
    "ragResponse": { "answer": "...", "sources": [...] },
    "validation": {
        "semanticScore": 0.82,
        "keywordCoverage": 0.75,
        "readabilityScore": 0.90,
        "finalScore": 0.81,
        "confidenceScore": 0.78
    }
}
```

Rate limit: 20 запросов / 60 секунд. Таймаут: 90 секунд (включает вызов doc-evaluator).

---

## 14. Отказоустойчивость и каскадная деградация

### 14.1 Трёхуровневая система таймаутов

```
Уровень 1: HTTP-запрос (60с)
├── Уровень 2: Обработка контекста (45с)
│   ├── Уровень 3: Каждый шаг FSM (30с)
│   │   ├── NORMALIZATION [30с]
│   │   ├── EXTRACTION [30с]
│   │   ├── EXACT_SEARCH [30с]
│   │   ├── GRAPH_EXPANSION [30с]
│   │   ├── REWRITING [30с]
│   │   ├── EXPANSION [30с]
│   │   ├── VECTOR_SEARCH [30с]
│   │   └── RERANKING [30с]
│   │
│   └── Вызов LLM (30с)
│
└── Формирование ответа
```

### 14.2 Каскадная деградация

Система спроектирована с **грациозной деградацией** (graceful degradation): отказ одного компонента снижает качество, но не прерывает обработку:

```
Сценарий 1: EXACT_SEARCH не нашёл узлы
    → REWRITING переформулирует запрос
    → EXPANSION обогащает синонимами
    → VECTOR_SEARCH ищет по эмбеддингам
    → Качество: хорошее (семантический контекст)

Сценарий 2: GRAPH_EXPANSION не нашёл связи
    → Пропускается графовый контекст
    → RERANKING работает с векторными результатами
    → Качество: приемлемое (без структурного контекста)

Сценарий 3: EXPANSION (синонимы) неуспешна
    → Используется оригинальный/переписанный запрос
    → VECTOR_SEARCH работает без расширения
    → Качество: приемлемое (меньший recall)

Сценарий 4: VECTOR_SEARCH пуст + нет точных узлов
    → RERANKING → FAILED
    → Возврат фоллбэк-ответа: "Информация не найдена"
    → Включены метаданные для диагностики

Сценарий 5: LLM таймаут
    → Возврат фоллбэк-ответа с метаданными источников
    → Пользователь видит найденные чанки без сгенерированного текста
```

### 14.3 Санитизация ошибок

```kotlin
private fun sanitizeErrorMessage(exception: Throwable): String = when (exception) {
    is IllegalArgumentException -> "Invalid request"
    is SQLException             -> "Database error occurred"  // Скрыты SQL-детали
    is ConnectException         -> "External service unavailable"
    else                        -> "An error occurred"
}
```

Внутренние детали (SQL-запросы, стек-трейсы, параметры подключения) **никогда** не возвращаются клиенту.

---

## 15. Модель данных

### 15.1 Сущность Chunk

```kotlin
@Entity
@Table(name = "chunk", schema = "doc_generator")
class Chunk(
    @Id var id: Long?,
    @ManyToOne var application: Application,
    @ManyToOne var node: Node,               // FK → Node (граф знаний)
    var source: String,                       // "code" | "doc" | "sql" | "log"
    var kind: String?,                        // "summary" | "explanation" | "tech" | "snippet"
    var langDetected: String = "ru",          // Определённый язык
    @Column(columnDefinition = "text")
    var content: String,                      // Текст чанка
    @Column(insertable = false, updatable = false)
    var contentTsv: String?,                  // PostgreSQL tsvector (полнотекстовый поиск)
    var contentHash: String?,                 // SHA-256 для дедупликации
    var tokenCount: Int?,                     // Количество токенов
    @Transient var emb: FloatArray?,          // Эмбеддинг (не в таблице напрямую)
    var embedModel: String?,                  // "bge-m3"
    var embedTs: OffsetDateTime?,             // Время генерации эмбеддинга
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: Map<String, Any>,           // Произвольные метаданные
    var createdAt: OffsetDateTime,
    var updatedAt: OffsetDateTime
)
```

### 15.2 Сущность NodeDoc

```kotlin
@Entity
class NodeDoc(
    @Id var id: Long?,
    @ManyToOne var node: Node,
    var status: NodeDocStatus,     // PENDING, GENERATING, DONE, STALE, ERROR
    var docText: String?,          // Сгенерированная документация
    var docHash: String?,          // SHA-256 для детекции изменений
    var generatedAt: OffsetDateTime?,
    var generatedBy: String?       // Имя модели
)
```

### 15.3 DTO ответов

```kotlin
data class RagResponse(
    val answer: String,                       // Ответ LLM
    val sources: List<RagSource>,             // Источники
    val metadata: RagQueryMetadata = RagQueryMetadata()
)

data class RagSource(
    val id: String,                           // ID чанка
    val content: String,                      // Текст чанка
    val metadata: Map<String, Any>,           // Метаданные
    val similarity: Double                    // Косинусное сходство [0, 1]
)

data class RagQueryMetadata(
    val originalQuery: String = "",
    val rewrittenQuery: String? = null,
    val expandedQueries: List<String> = emptyList(),
    val processingSteps: List<ProcessingStep> = emptyList(),
    val additionalData: Map<String, Any> = emptyMap()
)
```

### 15.4 Аудит-трейл: ProcessingStep

```kotlin
data class ProcessingStep(
    val advisorName: String,                  // Имя шага
    val input: String,                        // Входные данные
    val output: String,                       // Результат
    val timestamp: Long,                      // Время
    val stepType: ProcessingStepType?,        // Тип шага
    val status: ProcessingStepStatus,         // SUCCESS | FAILED | SKIPPED
    val durationMs: Long?                     // Длительность в мс
)
```

Каждый шаг FSM записывает `ProcessingStep`, формируя полный аудит-трейл обработки запроса.

---

## 16. Оценка качества ответов

### 16.1 Многомерная оценка

Система реализует оценку качества через внешний сервис `doc-evaluator`, возвращающий многомерные метрики:

```kotlin
data class EvaluationResult(
    val semanticScore: Double,      // Семантическая близость к исходному коду
    val keywordCoverage: Double,    // Покрытие ключевых терминов
    val readabilityScore: Double,   // Читаемость
    val llmScores: LlmScores,      // Оценки от LLM-судьи
    val finalScore: Double,         // Итоговая взвешенная оценка
    val scoreVariance: Double,      // Дисперсия оценок
    val confidenceScore: Double     // Уверенность в оценке
)
```

Sun et al. [33] исследовали саммаризацию кода в эпоху LLM, оценив пять техник промптинга (zero-shot, few-shot, chain-of-thought, critique, expert). Ahmed et al. [34] показали, что автоматическое обогащение промптов семантическим контекстом улучшает качество саммаризации — принцип, реализованный в нашей системе через графовый контекст.

### 16.2 Метрики оценки

| Метрика | Диапазон | Описание |
|---------|----------|----------|
| `semanticScore` | [0, 1] | Косинусное сходство между ответом и исходным кодом |
| `keywordCoverage` | [0, 1] | Доля ключевых терминов кода, упомянутых в ответе |
| `readabilityScore` | [0, 1] | Оценка читаемости текста (Flesch-подобная метрика) |
| `finalScore` | [0, 1] | Взвешенная комбинация всех метрик |
| `confidenceScore` | [0, 1] | Уверенность в итоговой оценке |

---

## 17. Тестирование

### 17.1 Тесты FSM-движка

**GraphRequestProcessorTest** (5 тестов):

| Тест | Что проверяется |
|------|-----------------|
| Happy Path | Последовательное выполнение шагов → COMPLETED |
| Cycle Detection | Обнаружение повторного визита → FAILED |
| Error Handling | Исключение в шаге → метаданные ошибки → FAILED |
| Invalid Transition | Неизвестный transitionKey → FAILED |
| Missing Step | Отсутствие обработчика → FAILED |

### 17.2 Тесты RAG-сервиса

**RagServiceImplTest** (6 тестов):

| Тест | Что проверяется |
|------|-----------------|
| Basic Flow | Чанки → LLM → RagResponse |
| Processing Failure | FAILED → фоллбэк без LLM-вызова |
| Query Rewriting | Переписанный запрос в метаданных |
| Deduplication | Дедупликация чанков по ID |
| Result Limiting | Top-5 отсортированных по similarity |
| Null Response | LLM вернул null → сообщение по умолчанию |

### 17.3 Тесты фильтрации

**ResultFilterServiceTest**:

| Тест | Что проверяется |
|------|-----------------|
| No Metadata | Без ключевых слов → все результаты |
| Exact Class Match | `\bUserService\b` → только совпадения |
| Exact Method Match | `\bgetUser\b` → только совпадения |
| Both Class+Method | OR-логика: любое совпадение проходит |
| Fuzzy Matching | CamelCase-сплит + частичное совпадение |
| Short Keywords | Длина < 3 → пропуск фильтрации |

### 17.4 Тесты отдельных шагов

11 тестовых классов покрывают каждый шаг FSM:
`NormalizationStepTest`, `ExtractionStepTest`, `ExactSearchStepTest`, `GraphExpansionStepTest`, `RewritingStepTest`, `ExpansionStepTest`, `VectorSearchStepTest`, `RerankingStepTest`.

**Технологии**: JUnit 5, MockK (мокирование зависимостей), AssertJ (fluent-ассерты). Без запуска БД — все репозитории замокированы.

---

## 18. Список литературы

[1] Lewis, P., Perez, E., Piktus, A., et al. *Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks.* In: NeurIPS 2020, pp. 9459-9474. URL: [arxiv.org/abs/2005.11401](https://arxiv.org/abs/2005.11401)

[2] Gao, Y., Xiong, Y., Gao, X., et al. *Retrieval-Augmented Generation for Large Language Models: A Survey.* arXiv:2312.10997, 2024. URL: [arxiv.org/abs/2312.10997](https://arxiv.org/abs/2312.10997)

[3] Singh, R., et al. *Agentic Retrieval-Augmented Generation: A Survey on Agentic RAG.* arXiv:2501.09136, 2025. URL: [arxiv.org/abs/2501.09136](https://arxiv.org/abs/2501.09136)

[4] Wu, Y., et al. *cAST: Enhancing Code Retrieval-Augmented Generation with Structural Chunking via Abstract Syntax Tree.* In: Findings of EMNLP 2025. URL: [aclanthology.org/2025.findings-emnlp.430](https://aclanthology.org/2025.findings-emnlp.430/)

[5] Shi, E., et al. *Survey of Code Search Based on Deep Learning.* ACM TOSEM, Vol. 33, No. 2, 2024. DOI: [10.1145/3628161](https://dl.acm.org/doi/10.1145/3628161)

[6] Fang, S., et al. *A Survey of Source Code Search: A 3-Dimensional Perspective.* ACM TOSEM, 2024. DOI: [10.1145/3656341](https://dl.acm.org/doi/10.1145/3656341)

[7] Lomshakov, V., Podivilov, A., Savin, S., et al. *ProConSuL: Project Context for Code Summarization with LLMs.* In: EMNLP 2024 Industry Track, pp. 866-880. DOI: [10.18653/v1/2024.emnlp-industry.65](https://aclanthology.org/2024.emnlp-industry.65/)

[8] Tao, Y., et al. *Retrieval-Augmented Code Generation: A Survey with Focus on Repository-Level Approaches.* arXiv:2510.04905, 2025. URL: [arxiv.org/abs/2510.04905](https://arxiv.org/abs/2510.04905)

[9] Vaswani, A., Shazeer, N., Parmar, N., et al. *Attention Is All You Need.* In: NeurIPS 2017. arXiv: [1706.03762](https://arxiv.org/abs/1706.03762)

[10] Brown, T.B., Mann, B., Ryder, N., et al. *Language Models are Few-Shot Learners.* In: NeurIPS 2020. arXiv: [2005.14165](https://arxiv.org/abs/2005.14165)

[11] Karpukhin, V., Oguz, B., Min, S., et al. *Dense Passage Retrieval for Open-Domain Question Answering.* In: EMNLP 2020, pp. 6769-6781. DOI: [10.18653/v1/2020.emnlp-main.550](https://aclanthology.org/2020.emnlp-main.550/)

[12] Reimers, N., Gurevych, I. *Sentence-BERT: Sentence Embeddings using Siamese BERT-Networks.* In: EMNLP 2019, pp. 3982-3992. DOI: [10.18653/v1/D19-1410](https://aclanthology.org/D19-1410/)

[13] Hindle, A., Barr, E.T., Gabel, M., Su, Z., Devanbu, P. *On the Naturalness of Software.* In: ICSE 2012. URL: [people.inf.ethz.ch](https://people.inf.ethz.ch/suz/publications/natural.pdf)

[14] Yli-Jyra, A., Koskenniemi, K. (Eds.). *Finite-State Methods and Natural Language Processing.* FSMNLP 2005, LNCS. Springer, 2006. URL: [link.springer.com](https://link.springer.com/book/10.1007/11780885)

[15] Wu, Y., et al. *StateFlow: Enhancing LLM Task-Solving through State-Driven Workflows.* arXiv:2403.11322, 2024. URL: [arxiv.org/abs/2403.11322](https://arxiv.org/abs/2403.11322)

[16] Xu, Y., et al. *DFA-RAG: Conversational Semantic Router for Large Language Model with Definite Finite Automaton.* arXiv:2402.04411, 2024. URL: [arxiv.org/abs/2402.04411](https://arxiv.org/abs/2402.04411)

[17] Liu, Z., et al. *Is Semantic Chunking Worth the Computational Cost?* arXiv:2410.13070, 2024. URL: [arxiv.org/abs/2410.13070](https://arxiv.org/abs/2410.13070)

[18] Merola, C., Singh, J. *Reconstructing Context: Evaluating Advanced Chunking Strategies for Retrieval-Augmented Generation.* arXiv:2504.19754, 2025. URL: [arxiv.org/abs/2504.19754](https://arxiv.org/abs/2504.19754)

[19] Gamma, E., Helm, R., Johnson, R., Vlissides, J. *Design Patterns: Elements of Reusable Object-Oriented Software.* Addison-Wesley, 1994. ISBN: 0-201-63361-2.

[20] Chen, J., Xiao, S., Zhang, P., et al. *BGE M3-Embedding: Multi-Lingual, Multi-Functionality, Multi-Granularity Text Embeddings Through Self-Knowledge Distillation.* In: Findings of ACL 2024, pp. 2318-2335. URL: [aclanthology.org/2024.findings-acl.137](https://aclanthology.org/2024.findings-acl.137/)

[21] Desislavov, R., et al. *Production-Grade Local LLM Inference on Apple Silicon: A Comparative Study.* arXiv:2511.05502, 2025. URL: [arxiv.org/abs/2511.05502](https://arxiv.org/abs/2511.05502)

[22] Turney, P.D., Pantel, P. *From Frequency to Meaning: Vector Space Models of Semantics.* JAIR, Vol. 37, pp. 141-188, 2010. DOI: [10.1613/jair.2934](https://dl.acm.org/doi/10.5555/1861751.1861756)

[23] Malkov, Y.A., Yashunin, D.A. *Efficient and Robust Approximate Nearest Neighbor Search Using Hierarchical Navigable Small World Graphs.* IEEE TPAMI, Vol. 42, No. 4, pp. 824-836, 2020. DOI: [10.1109/TPAMI.2018.2889473](https://ieeexplore.ieee.org/document/8594636)

[24] Jegou, H., Douze, M., Schmid, C. *Product Quantization for Nearest Neighbor Search.* IEEE TPAMI, Vol. 33, No. 1, 2011. DOI: [10.1109/TPAMI.2010.57](https://ieeexplore.ieee.org/document/5432202)

[25] pgvector: Open-source vector similarity search for Postgres. URL: [github.com/pgvector/pgvector](https://github.com/pgvector/pgvector)

[26] Abdelaziz, I., Srinivas, K., Dolby, J., McCusker, J.P. *A Toolkit for Generating Code Knowledge Graphs.* In: K-CAP 2021. DOI: [10.1145/3460210.3493578](https://dl.acm.org/doi/10.1145/3460210.3493578)

[27] *Knowledge Graph Based Repository-Level Code Generation.* arXiv:2505.14394, 2025. URL: [arxiv.org/abs/2505.14394](https://arxiv.org/abs/2505.14394)

[28] Sawarkar, K., Mangal, A., Shivakumar, S.R. *Blended RAG: Improving RAG Accuracy with Semantic Search and Hybrid Query-Based Retrievers.* arXiv:2404.07220, 2024. URL: [arxiv.org/abs/2404.07220](https://arxiv.org/abs/2404.07220)

[29] Khattab, O., Zaharia, M. *ColBERT: Efficient and Effective Passage Search via Contextualized Late Interaction over BERT.* In: SIGIR 2020. DOI: [10.1145/3397271.3401075](https://dl.acm.org/doi/10.1145/3397271.3401075)

[30] Liu, N.F., Lin, K., Hewitt, J., et al. *Lost in the Middle: How Language Models Use Long Contexts.* arXiv:2307.03172, 2023. URL: [arxiv.org/abs/2307.03172](https://arxiv.org/abs/2307.03172)

[31] Dong, Q., Li, L., Dai, D., et al. *A Survey on In-context Learning.* In: EMNLP 2024, pp. 1107-1128. DOI: [10.18653/v1/2024.emnlp-main.64](https://aclanthology.org/2024.emnlp-main.64/)

[32] Kachris, C. *Sustainable LLM Inference for Edge AI: Evaluating Quantized LLMs for Energy Efficiency, Output Accuracy, and Inference Latency.* ACM Transactions on IoT, 2025. DOI: [10.1145/3767742](https://dl.acm.org/doi/10.1145/3767742)

[33] Sun, W., Miao, Y., Li, Y., et al. *Source Code Summarization in the Era of Large Language Models.* In: ICSE 2025. arXiv: [2407.07959](https://arxiv.org/abs/2407.07959)

[34] Ahmed, T., Pai, K.S., Devanbu, P., Barr, E.T. *Automatic Semantic Augmentation of Language Model Prompts (for Code Summarization).* In: ICSE 2024. DOI: [10.1145/3597503.3639183](https://dl.acm.org/doi/10.1145/3597503.3639183)

[35] Kusupati, A., Bhatt, G., Rege, A., et al. *Matryoshka Representation Learning.* In: NeurIPS 2022. arXiv: [2205.13147](https://arxiv.org/abs/2205.13147)

[36] Alon, U., Zilberstein, M., Levy, O., Yahav, E. *code2vec: Learning Distributed Representations of Code.* In: POPL 2019, Article 40. DOI: [10.1145/3290353](https://dl.acm.org/doi/10.1145/3290353)

[37] Feng, Z., Guo, D., Tang, D., et al. *CodeBERT: A Pre-Trained Model for Programming and Natural Languages.* In: Findings of EMNLP 2020. URL: [aclanthology.org/2020.findings-emnlp.139](https://aclanthology.org/2020.findings-emnlp.139/)

[38] Allamanis, M., Barr, E.T., Devanbu, P., Sutton, C. *A Survey of Machine Learning for Big Code and Naturalness.* ACM Computing Surveys, Vol. 51, No. 4, 2018. DOI: [10.1145/3212695](https://dl.acm.org/doi/10.1145/3212695)
