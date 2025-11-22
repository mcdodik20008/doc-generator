# Алгоритм разбиения узлов графа на чанки (Chunking)

## Обзор

Данный документ описывает процесс разбиения узлов графа кода на чанки (chunks) для последующего использования в RAG (Retrieval-Augmented Generation). Чанки представляют собой структурированные фрагменты кода или документации, которые могут быть проиндексированы, встроены в векторное пространство и использованы для поиска релевантного контекста при генерации документации через LLM.

## Инструменты

- **ChunkStrategy** - стратегии разбиения узлов на чанки
- **ChunkWriter** - сохранение планов чанков в БД
- **ChunkRunStore** - отслеживание выполнения процесса chunking

## Основной процесс

### Фаза 1: Инициализация процесса chunking

**Сервис**: `ChunkBuildOrchestratorImpl.start()`

#### Шаг 1.1: Создание запроса

```kotlin
data class ChunkBuildRequest(
    val applicationId: Long,           // ID приложения
    val strategy: String,               // "per-node" (стратегия разбиения)
    val dryRun: Boolean = false,        // Только подсчет, без записи
    val limitNodes: Long? = null,       // Лимит узлов для обработки
    val batchSize: Int = 200,           // Размер страницы при чтении узлов
    val includeKinds: Set<String>? = null, // Фильтр по типам узлов (CLASS, METHOD, etc.)
    val withEdgesRelations: Boolean = true // Подтягивать рёбра для связей
)
```

#### Шаг 1.2: Выбор стратегии

```kotlin
val strategy = strategies[req.strategy] ?: error("Unknown strategy: ${req.strategy}")
```

**Доступные стратегии**:
- `"per-node"` - один чанк на узел (PerNodeChunkStrategy)

#### Шаг 1.3: Создание run-записи

```kotlin
val run = runStore.create(req.applicationId, req.strategy)
```

Создаётся запись о запуске процесса для отслеживания прогресса и статуса.

### Фаза 2: Пагинация и чтение узлов

#### Шаг 2.1: Настройка пагинации

```kotlin
val pageSize = max(50, req.batchSize)
var page = 0
```

#### Шаг 2.2: Фильтрация по типам узлов

```kotlin
val kindsFilter: Set<NodeKind>? = req.includeKinds
    ?.mapNotNull { raw ->
        try {
            NodeKind.valueOf(raw.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }
    }?.toSet()
    ?.takeIf { it.isNotEmpty() }
```

Если указан фильтр, обрабатываются только узлы указанных типов.

#### Шаг 2.3: Чтение узлов страницами

```kotlin
while (true) {
    val pageReq = PageRequest.of(page, pageSize, Sort.by("id").ascending())
    val pageData = if (kindsFilter.isNullOrEmpty()) {
        nodeRepo.findAllByApplicationId(req.applicationId, pageReq)
    } else {
        nodeRepo.findPageAllByApplicationIdAndKindIn(req.applicationId, kindsFilter, pageReq)
    }
    
    val nodes = pageData.toList()
    if (nodes.isEmpty()) break
    
    // Обработка узлов...
    page++
}
```

Узлы читаются страницами для оптимизации памяти.

### Фаза 3: Загрузка рёбер (опционально)

#### Шаг 3.1: Батчевая загрузка рёбер

```kotlin
val edgesBySrc: Map<Long, List<Edge>> = if (req.withEdgesRelations) {
    val ids = nodes.mapNotNull { it.id }
    if (ids.isEmpty()) {
        emptyMap()
    } else {
        edgeRepo.findAllBySrcIdIn(ids).groupBy { it.src!!.id!! }
    }
} else {
    emptyMap()
}
```

**Оптимизация**: Рёбра загружаются батчем для всех узлов страницы, чтобы избежать N+1 запросов.

### Фаза 4: Построение планов чанков

**Стратегия**: `PerNodeChunkStrategy.buildChunks()`

#### Шаг 4.1: Анализ узла

```kotlin
val hasDoc = !node.signature.isNullOrBlank() || !node.docComment.isNullOrBlank()
```

Определяется, есть ли у узла документация (сигнатура или doc-комментарий).

#### Шаг 4.2: Построение sectionPath

```kotlin
val sectionPath = buildList {
    node.packageName?.split('.')?.let { addAll(it) }
    node.name?.let { add(it) }
}
```

Создаётся иерархический путь: `["com", "bftcom", "package", "ClassName"]`

#### Шаг 4.3: Извлечение связей

```kotlin
val relations = edges
    .asSequence()
    .filter { it.kind == EdgeKind.CALLS }
    .map { e -> RelationHint(kind = "CALLS", dstNodeId = e.dst.id ?: -1, confidence = 0.7) }
    .toList()
```

Извлекаются связи типа `CALLS` для последующего использования в RAG.

### Фаза 5: Создание ChunkPlan

#### Шаг 5.1: DOC-чанк (если есть документация)

```kotlin
if (hasDoc) {
    ChunkPlan(
        id = "${node.id}:doc:explanation",  // Детерминированный ID
        nodeId = node.id!!,
        source = "doc",                     // Источник: документация
        kind = "explanation",               // Тип: объяснение
        lang = "ru",                        // Язык документации
        spanLines = toRange(node.lineStart, node.lineEnd),
        title = node.fqn,                   // FQN как заголовок
        sectionPath = sectionPath,
        relations = relations,
        pipeline = PipelinePlan(
            stages = listOf("render-doc", "embed", "link-edges"),
            params = mapOf(
                "signature" to (node.signature ?: ""),
                "hasDocComment" to (!node.docComment.isNullOrBlank())
            ),
            service = ServiceMeta(strategy = "per-node", priority = priorityFor(node))
        ),
        node = node
    )
}
```

**Pipeline stages для DOC-чанка**:
1. `render-doc` - рендеринг документации из сигнатуры/doc-комментария
2. `embed` - создание векторного представления
3. `link-edges` - связывание с рёбрами графа

#### Шаг 5.2: CODE-чанк (если нет документации)

```kotlin
else {
    ChunkPlan(
        id = "${node.id}:code:snippet",
        nodeId = node.id!!,
        source = "code",                    // Источник: код
        kind = "snippet",                   // Тип: фрагмент кода
        lang = node.lang.name,              // Язык кода (KOTLIN, JAVA, etc.)
        spanLines = toRange(node.lineStart, node.lineEnd),
        title = node.fqn,
        sectionPath = sectionPath,
        relations = relations,
        pipeline = PipelinePlan(
            stages = listOf("extract-snippet", "summarize", "embed", "link-edges"),
            params = mapOf(
                "filePath" to (node.filePath ?: ""),
                "hasSourceInNode" to (node.sourceCode != null)
            ),
            service = ServiceMeta(strategy = "per-node", priority = priorityFor(node))
        ),
        node = node
    )
}
```

**Pipeline stages для CODE-чанка**:
1. `extract-snippet` - извлечение фрагмента кода из файла
2. `summarize` - суммаризация кода через LLM
3. `embed` - создание векторного представления
4. `link-edges` - связывание с рёбрами графа

#### Шаг 5.3: Определение приоритета

```kotlin
private fun priorityFor(node: Node): Int = when (node.kind.name) {
    "ENDPOINT", "METHOD" -> 10  // Высокий приоритет
    "CLASS" -> 5                // Средний приоритет
    else -> 0                   // Низкий приоритет
}
```

Приоритет влияет на порядок обработки в pipeline.

### Фаза 6: Сохранение планов чанков

**Сервис**: `ChunkWriterImpl.savePlan()`

#### Шаг 6.1: Батчевое сохранение

```kotlin
val plansBuffer = mutableListOf<ChunkPlan>()

for (n in nodes) {
    val plan = strategy.buildChunks(n, edges)
    
    if (req.dryRun) {
        skipped += plan.size.toLong()
        continue
    }
    
    plansBuffer += plan
    
    // Защита от переполнения памяти
    if (plansBuffer.size >= 1000) {
        val res = chunkWriter.savePlan(plansBuffer.toList())
        written += res.written
        skipped += res.skipped
        plansBuffer.clear()
    }
}
```

Планы накапливаются в буфере и сохраняются батчами по 1000 штук.

#### Шаг 6.2: Создание Chunk из ChunkPlan

```kotlin
for (plan in plans) {
    val existing = chunkRepo.findTopByNodeIdOrderByCreatedAtDesc(nodeId)
    
    val chunk = Chunk(
        id = existing?.id,                    // Обновление существующего или создание нового
        application = plan.node.application,
        node = plan.node,
        source = plan.source,                 // "code" | "doc"
        kind = plan.kind,                     // "snippet" | "explanation"
        langDetected = plan.lang,
        contentRaw = null,                    // Заполнится позже в pipeline
        content = "null",                     // Заполнится позже
        contentTsv = null,                    // Генерируется БД
        contentHash = null,                   // Вычислится позже
        tokenCount = null,                    // Вычислится позже
        chunkIndex = 0,
        spanLines = plan.spanLines?.let { "[${it.first},${it.last}]" },
        spanChars = null,
        title = plan.title,
        sectionPath = plan.sectionPath,
        usesMd = null,
        usedByMd = null,
        emb = null,                           // Векторное представление
        embedModel = null,
        embedTs = null,
        explainMd = null,                    // LLM-объяснение
        explainQuality = emptyMap(),
        relations = plan.relations.map { 
            mapOf("kind" to it.kind, "dst_node_id" to it.dstNodeId) 
        },
        usedObjects = emptyList(),
        pipeline = mapOf(
            "stages" to plan.pipeline.stages,
            "params" to plan.pipeline.params,
            "service" to plan.pipeline.service
        ),
        freshnessAt = OffsetDateTime.now(),
        rankBoost = 1.0f,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
    )
    
    chunkRepo.save(chunk)
}
```

**Важно**: На этом этапе создаётся только структура чанка. Контент (`content`, `contentRaw`) и эмбеддинги (`emb`) заполняются позже в pipeline.

### Фаза 7: Обработка pipeline stages

Pipeline stages обрабатываются асинхронно после создания чанков.

#### Stage 1: extract-snippet (для CODE-чанков)

- Чтение исходного кода из файла по `filePath` и `spanLines`
- Извлечение фрагмента кода
- Сохранение в `contentRaw` и `content`

#### Stage 2: render-doc (для DOC-чанков)

- Рендеринг документации из `signature` и `docComment`
- Форматирование в Markdown
- Сохранение в `content`

#### Stage 3: summarize (для CODE-чанков)

- Генерация краткого описания кода через LLM
- Сохранение в `explainMd`

#### Stage 4: embed

- Создание векторного представления через embedding модель
- Сохранение в `emb` (vector(1024))
- Заполнение `embedModel` и `embedTs`

#### Stage 5: link-edges

- Обогащение связями из графа
- Заполнение `usesMd` и `usedByMd` (человекочитаемые описания связей)

## Структура ChunkPlan

```kotlin
data class ChunkPlan(
    val id: String,              // Детерминированный ID: "{nodeId}:{source}:{kind}"
    val nodeId: Long,            // Ссылка на узел
    val source: String,          // "code" | "doc"
    val kind: String,            // "snippet" | "explanation"
    val lang: String?,           // Язык контента
    val spanLines: IntRange?,    // Диапазон строк в исходнике
    val title: String?,          // Заголовок (обычно FQN)
    val sectionPath: List<String>, // Иерархический путь
    val relations: List<RelationHint>, // Подсказки по связям
    val pipeline: PipelinePlan,  // План обработки
    val node: Node               // Ссылка на узел
)
```

## Структура Chunk

```kotlin
data class Chunk(
    val id: Long?,
    val application: Application,
    val node: Node,
    
    // Тип/источник
    val source: String,          // "code" | "doc" | "sql" | "log"
    val kind: String?,           // "summary" | "explanation" | "snippet"
    val langDetected: String?,   // "ru" | "en" | "kotlin" | "java"
    
    // Контент
    val contentRaw: String?,     // Исходный текст (до нормализации)
    val content: String,         // Нормализованный текст
    val contentTsv: String?,     // tsvector для полнотекстового поиска (генерируется БД)
    val contentHash: String?,    // SHA-256 для дедупликации
    val tokenCount: Int?,        // Количество токенов
    
    // Позиция
    val chunkIndex: Int?,        // Порядковый номер в разбиении узла
    val spanLines: String?,      // INT4RANGE: [line_start, line_end]
    val spanChars: String?,      // INT8RANGE: [char_start, char_end]
    
    // Контекст
    val title: String?,          // Заголовок
    val sectionPath: List<String>, // Иерархический путь
    val usesMd: String?,         // Как мы используем зависимости (Markdown)
    val usedByMd: String?,       // Где нас используют (Markdown)
    
    // Векторное представление
    val emb: FloatArray?,        // vector(1024) - эмбеддинг
    val embedModel: String?,     // Модель эмбеддинга
    val embedTs: OffsetDateTime?, // Время создания эмбеддинга
    
    // LLM-объяснение
    val explainMd: String?,      // LLM-объяснение чанка (Markdown)
    val explainQuality: Map<String, Any>, // Метрики качества
    
    // Связи
    val relations: List<Map<String, Any>>, // Машиночитаемые связи
    val usedObjects: List<Map<String, Any>>, // Используемые объекты
    
    // Pipeline
    val pipeline: Map<String, Any>, // Информация о pipeline
    val freshnessAt: OffsetDateTime?, // Время актуальности
    val rankBoost: Float,        // Множитель ранга для поиска
    
    // Служебное
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
```

## Стратегии chunking

### PerNodeChunkStrategy

**Принцип**: Один чанк на узел.

**Логика**:
- Если у узла есть документация (`signature` или `docComment`) → создаётся DOC-чанк
- Если документации нет → создаётся CODE-чанк

**Преимущества**:
- Простота реализации
- Прямое соответствие узлам графа
- Легко отслеживать изменения

**Недостатки**:
- Большие классы могут быть слишком большими для одного чанка
- Не учитывает семантическую структуру кода

**Приоритеты**:
- `ENDPOINT`, `METHOD` → 10 (высокий)
- `CLASS` → 5 (средний)
- Остальные → 0 (низкий)

## Pipeline stages

### extract-snippet

**Для**: CODE-чанки

**Процесс**:
1. Чтение исходного файла по `filePath`
2. Извлечение фрагмента по `spanLines`
3. Сохранение в `contentRaw` и `content`

### render-doc

**Для**: DOC-чанки

**Процесс**:
1. Извлечение `signature` и `docComment` из узла
2. Форматирование в Markdown
3. Сохранение в `content`

### summarize

**Для**: CODE-чанки

**Процесс**:
1. Генерация краткого описания кода через LLM
2. Сохранение в `explainMd`

### embed

**Для**: Все чанки

**Процесс**:
1. Создание векторного представления через embedding модель
2. Сохранение в `emb` (vector(1024))
3. Заполнение метаданных (`embedModel`, `embedTs`)

### link-edges

**Для**: Все чанки

**Процесс**:
1. Загрузка рёбер из графа
2. Генерация человекочитаемых описаний связей
3. Заполнение `usesMd` и `usedByMd`

## Оптимизации

1. **Пагинация**: Узлы читаются страницами для экономии памяти
2. **Батчевая загрузка рёбер**: Избежание N+1 запросов
3. **Буферизация планов**: Сохранение батчами по 1000 штук
4. **Dry-run режим**: Подсчет без записи для тестирования
5. **Фильтрация по типам**: Обработка только нужных типов узлов

## Обработка ошибок

1. **Ошибка при построении плана**: Логируется, узел пропускается
2. **Ошибка при сохранении**: Логируется, счётчик ошибок увеличивается
3. **Ошибка в pipeline**: Обрабатывается на этапе выполнения stages

## Статистика

После завершения возвращается:
- `processedNodes` - количество обработанных узлов
- `writtenChunks` - количество созданных чанков
- `skippedChunks` - количество пропущенных чанков (dry-run)
- `pages` - количество страниц
- `duration` - время выполнения
- `rate` - скорость обработки (узлов/сек)

## Схема потока данных

```
ChunkBuildRequest
  ↓
ChunkBuildOrchestratorImpl.start()
  ├─→ Выбор стратегии (PerNodeChunkStrategy)
  ├─→ Создание run-записи
  └─→ Цикл по страницам узлов:
      ├─→ Загрузка узлов (пагинация)
      ├─→ Батчевая загрузка рёбер (опционально)
      └─→ Для каждого узла:
          ├─→ PerNodeChunkStrategy.buildChunks()
          │   ├─→ Анализ: есть ли документация?
          │   ├─→ Построение sectionPath
          │   ├─→ Извлечение relations
          │   └─→ Создание ChunkPlan (DOC или CODE)
          │
          └─→ Накопление в буфере
              ↓
      Батчевое сохранение (каждые 1000 планов)
          ↓
      ChunkWriterImpl.savePlan()
          ├─→ Создание Chunk из ChunkPlan
          └─→ Сохранение в БД
              ↓
      Pipeline stages (асинхронно):
          ├─→ extract-snippet / render-doc
          ├─→ summarize (для CODE)
          ├─→ embed
          └─→ link-edges
```

## Использование чанков в RAG

После создания чанков они используются для:

1. **Векторный поиск**: Поиск релевантных чанков по семантическому сходству через `emb`
2. **Полнотекстовый поиск**: Поиск по `contentTsv` (tsvector)
3. **Графовый контекст**: Использование `relations` для расширения контекста
4. **Иерархическая навигация**: Использование `sectionPath` для структурированного поиска

## Связанные компоненты

- `ChunkStrategy` - стратегии разбиения
- `ChunkWriter` - сохранение чанков
- `ChunkRunStore` - отслеживание выполнения
- `ChunkRepository` - доступ к БД
- `EmbeddingClient` - создание векторных представлений
- `Pipeline stages` - обработка контента

