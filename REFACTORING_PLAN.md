# План рефакторинга архитектурно не проработанных участков

<!-- markdownlint-disable MD012 MD022 MD029 MD031 MD032 -->

## Обзор

Данный документ описывает выявленные архитектурные проблемы и план их решения через рефакторинг.

## Выявленные проблемы

### 2. NodeBuilder - Смешение ответственностей

**Проблема**: Класс `NodeBuilder` (354 строки) смешивает:
- Валидацию данных
- Нормализацию кода
- Вычисление хешей
- Кэширование
- Создание/обновление узлов
- Управление статистикой

**Текущая структура**:
```kotlin
class NodeBuilder {
    fun upsertNode(...) // 95 строк - слишком длинный метод
    private fun updateExistingNode(...) // 85 строк
    private fun validateNodeData(...) // 55 строк
    private fun computeCodeHash(...) // 15 строк
    private fun countLinesNormalized(...) // 5 строк
    // + статистика и кэширование
}
```

**План рефакторинга**:

1. **Выделить валидатор**:
   ```kotlin
   interface NodeValidator {
       fun validate(fqn: String, span: IntRange?, parent: Node?, sourceCode: String?)
   }
   ```

2. **Выделить нормализатор кода**:
   ```kotlin
   interface CodeNormalizer {
       fun normalize(sourceCode: String?, maxSize: Int): String?
       fun countLines(sourceCode: String): Int
   }
   ```

3. **Выделить хешер**:
   ```kotlin
   interface CodeHasher {
       fun computeHash(sourceCode: String?): String?
   }
   ```

4. **Выделить стратегию обновления**:
   ```kotlin
   interface NodeUpdateStrategy {
       fun update(existing: Node, newData: NodeData): Node
   }
   ```

5. **Упростить NodeBuilder**:
   - Оставить только координацию
   - Использовать композицию вместо всех ответственностей

**Приоритет**: Средний  
**Оценка времени**: 1-2 дня  
**Файлы для изменения**:
- `contexts/graph/impl/src/main/kotlin/com/bftcom/docgenerator/graph/impl/node/builder/NodeBuilder.kt`

---

### 3. HttpBytecodeAnalyzerImpl - Сложный анализ байткода

**Проблема**: Класс содержит:
- Анализ HTTP-вызовов
- Анализ Kafka-вызовов
- Анализ Camel-вызовов
- Построение call graph
- Поиск parent clients
- Построение method summaries
- Внутренний класс `HttpAnalysisClassVisitor` (550+ строк)

**План рефакторинга**:

1. **Выделить отдельные анализаторы**:
   ```kotlin
   interface IntegrationAnalyzer {
       fun analyze(jarFile: File): IntegrationAnalysisResult
   }
   
   class HttpIntegrationAnalyzer : IntegrationAnalyzer
   class KafkaIntegrationAnalyzer : IntegrationAnalyzer
   class CamelIntegrationAnalyzer : IntegrationAnalyzer
   ```

2. **Выделить построитель call graph**:
   ```kotlin
   interface CallGraphBuilder {
       fun build(calls: Map<MethodId, Set<MethodId>>): CallGraph
   }
   ```

3. **Выделить построитель method summaries**:
   ```kotlin
   interface MethodSummaryBuilder {
       fun build(httpCalls: List<HttpCallSite>, ...): Map<MethodId, MethodSummary>
   }
   ```

4. **Выделить visitor'ы в отдельные классы**:
   - `HttpAnalysisClassVisitor` → отдельный файл
   - Возможно разделить на `HttpCallVisitor`, `KafkaCallVisitor`, `CamelCallVisitor`

**Приоритет**: Средний  
**Оценка времени**: 2-3 дня  
**Файлы для изменения**:
- `contexts/library/impl/src/main/kotlin/com/bftcom/docgenerator/library/impl/bytecode/HttpBytecodeAnalyzerImpl.kt`

---

### 4. ExplainRequestFactory - Object с большой логикой

**Проблема**: `object ExplainRequestFactory` содержит:
- Метод `buildRichHints()` (110 строк) - слишком сложный
- Множество вложенных функций
- Прямой доступ к метаданным узла

**План рефакторинга**:

1. **Выделить построитель hints**:
   ```kotlin
   interface HintsBuilder {
       fun build(chunk: Chunk, node: Node): String
   }
   
   class RichHintsBuilder : HintsBuilder {
       // Разбить на отдельные методы:
       // - buildBasicInfo()
       // - buildSignatures()
       // - buildAnnotations()
       // - buildKDoc()
       // - buildGraphHints()
       // - buildInstructions()
   }
   ```

2. **Выделить экстракторы метаданных**:
   ```kotlin
   interface MetadataExtractor {
       fun extractAnnotations(meta: Map<*, *>): List<String>
       fun extractImports(meta: Map<*, *>): List<String>
       // и т.д.
   }
   ```

3. **Преобразовать в класс с dependency injection**:
   - Вместо `object` использовать `@Component`
   - Инжектить зависимости через конструктор

**Приоритет**: Низкий  
**Оценка времени**: 1 день  
**Файлы для изменения**:
- `contexts/chunking/impl/src/main/kotlin/com/bftcom/docgenerator/chunking/factory/ExplainRequestFactory.kt`

---

### 5. RagServiceImpl - Длинный метод ask()

**Проблема**: Метод `ask()` (150 строк) делает слишком много:
- Обработка запроса через цепочку advisors
- Множественные поиски (основной + дополнительные)
- Объединение результатов
- Фильтрация
- Формирование контекста
- Генерация ответа

**План рефакторинга**:

1. **Выделить поисковый сервис**:
   ```kotlin
   interface RagSearchService {
       fun search(processingContext: QueryProcessingContext): List<SearchResult>
   }
   ```

2. **Выделить построитель контекста**:
   ```kotlin
   interface RagContextBuilder {
       fun build(exactNodes: List<Node>?, neighborNodes: List<Node>?, searchResults: List<SearchResult>): String
   }
   ```

3. **Выделить генератор ответа**:
   ```kotlin
   interface RagResponseGenerator {
       fun generate(context: String, query: String, sessionId: String): String
   }
   ```

4. **Упростить RagServiceImpl**:
   - Оставить только оркестрацию
   - Использовать композицию сервисов

**Приоритет**: Средний  
**Оценка времени**: 1-2 дня  
**Файлы для изменения**:
- `contexts/rag/impl/src/main/kotlin/com/bftcom/docgenerator/rag/impl/RagServiceImpl.kt`

---

### 6. Отсутствие абстракций для работы с метаданными

**Проблема**: Повсеместное использование `as? Map<*, *>` и `@Suppress("UNCHECKED_CAST")`:
- В `ExplainRequestFactory`
- В `GraphLinkerImpl`
- В других местах

**План рефакторинга**:

1. **Создать типобезопасные обёртки**:
   ```kotlin
   data class NodeMetadata(
       val annotations: List<String> = emptyList(),
       val imports: List<String> = emptyList(),
       val ownerFqn: String? = null,
       val paramTypes: List<String>? = null,
       val returnType: String? = null,
       // и т.д.
   ) {
       companion object {
           fun from(meta: Map<String, Any>?): NodeMetadata {
               // Безопасное извлечение с дефолтными значениями
           }
       }
   }
   ```

2. **Использовать мапперы**:
   ```kotlin
   interface NodeMetadataMapper {
       fun toNodeMetadata(meta: Map<String, Any>?): NodeMetadata
   }
   ```

**Приоритет**: Низкий  
**Оценка времени**: 1 день  
**Файлы для изменения**:
- Множество файлов, но изменения небольшие

---

### 7. Дублирование логики создания виртуальных узлов

**Проблема**: Методы `getOrCreateEndpointNode()` и `getOrCreateTopicNode()` в `GraphLinkerImpl` дублируют логику:
- Поиск существующего узла
- Создание нового узла
- Обработка ошибок

**План рефакторинга**:

1. **Создать общий интерфейс**:
   ```kotlin
   interface VirtualNodeFactory {
       fun <T : Node> getOrCreate(
           fqn: String,
           factory: () -> T,
           index: NodeIndex
       ): Pair<T?, Boolean>
   }
   ```

2. **Использовать builder pattern для создания узлов**:
   ```kotlin
   class EndpointNodeBuilder {
       fun withUrl(url: String): EndpointNodeBuilder
       fun withHttpMethod(method: String?): EndpointNodeBuilder
       fun build(application: Application): Node
   }
   ```

**Приоритет**: Низкий  
**Оценка времени**: 0.5 дня  
**Файлы для изменения**:
- `contexts/graph/impl/src/main/kotlin/com/bftcom/docgenerator/graph/impl/linker/GraphLinkerImpl.kt`

---

### 8. Иерархическая генерация документации узлов (KDoc → код → дайджесты зависимостей)

**Проблема**: текущий пайплайн заполняет `chunk.content_raw` и затем `chunk.content`, что удобно для RAG-чанков, но не даёт каноничной документации "на узел" и затрудняет агрегацию на уровень CLASS/PACKAGE без раздувания контекста исходниками и соседями.

**Цель**: внедрить node-level документацию с тремя полями:
- `doc_public`: текст "для обывателя"
- `doc_tech`: текст "для технического специалиста"
- `doc_digest`: короткая выжимка (строгий формат) для сборки контекста на верхних уровнях

Технический контекст, который реально скармливаем LLM (KDoc/код/дайджесты), хранить как метаданные сборки (json), а не как большой текстовый столбец.

#### 8.1. Формальные сущности и связи

Источник истины:
- `kernel/domain/src/main/kotlin/com/bftcom/docgenerator/domain/enums/NodeKind.kt`
- `kernel/domain/src/main/kotlin/com/bftcom/docgenerator/domain/enums/EdgeKind.kt`

Требование: генератор должен использовать `NodeKind`/`EdgeKind` как контракт. Детект рёбер может улучшаться со временем, но генерация должна корректно работать при неполном графе.

#### 8.2. Правила сборки контекста (главный нюанс)

Общий принцип: чем выше уровень сущности (METHOD → CLASS → PACKAGE → MODULE/REPO), тем больше абстракции и тем меньше сырого кода в контексте.

**METHOD (NodeKind.METHOD)**:
- 1) KDoc/аннотации/сигнатура — первичный источник смысла (самое важное)
- 2) исходники метода — основной объём "фактов" (только тело метода, без соседних нод по умолчанию)
- 3) `doc_digest` зависимостей — дополнительная справка (например, чтобы понимать смысл вызовов вида `service.getDataWithTmpNumber(...)`)

Список зависимостей метода строится по рёбрам (приоритет настраивается):
- CALLS_CODE, THROWS
- READS/WRITES/QUERIES
- CALLS_HTTP/CALLS_GRPC, PRODUCES/CONSUMES
- CONFIGURES/CIRCUIT_BREAKER_TO/RETRIES_TO/TIMEOUTS_TO
- DEPENDS_ON (низкий приоритет как общий сигнал)

Если `doc_digest` зависимости отсутствует, метод всё равно документируется (не блокируем очередь), но отмечаем `deps_missing=true` в метаданных для последующего refine-прохода.

**CLASS/INTERFACE/ENUM/RECORD**:
- KDoc/аннотации/наследование/роль (рамка)
- основной контекст — `doc_digest` детей (методы/поля по CONTAINS), приоритет: public/protected
- исходники класса: только декларации/сигнатуры при необходимости (без тел методов)

MVP: "дети класса" = методы/поля. Наследники (subclasses) — отдельная фича позже.

**PACKAGE/MODULE/REPO**:
- контекст собирается из `doc_digest` нижнего уровня
- при переполнении контекста делаем промежуточный digest (сжатие) вместо добавления исходников

#### 8.3. Модель хранения и миграция (Liquibase)

Документацию "на узел" хранить в таблице `doc_generator.node_doc` (она уже создана в `00_init.sql`, но в коде не используется).

План миграции:
1) Добавить в `doc_generator.node_doc` колонки:
   - `doc_public TEXT`
   - `doc_tech TEXT`
   - `doc_digest TEXT`
2) Метаданные сборки контекста и версию промпта/модели писать в `node_doc.model_meta` (jsonb), например:
   - `prompt_id`, `model`, `temperature`
   - `context_budget`
   - `included`: список `{node_id, kind, edge_kind, level, token_estimate}`
   - `deps_missing`: boolean
   - `source_hashes`: code_hash/doc_comment hash

Liquibase:
- создать `kernel/domain/src/main/resources/liquibase/migration/08_add_node_doc_variants.sql`
- добавить changeset в `kernel/domain/src/main/resources/liquibase/db.changelog-master.xml` после `06-add-metadata-to-chunk`

#### 8.4. Пайплайн генерации (действия по коду)

Текущее состояние:
- `contexts/chunking/impl/.../RawContentFillerScheduler.kt` заполняет `chunk.content_raw`
- `contexts/chunking/impl/.../ContentFillerScheduler.kt` переписывает `chunk.content`

План действий:
1) В `kernel/domain` добавить Entity и Repository для `node_doc` (если их нет).
2) В `contexts/chunking/impl` (или новом контексте "docs") выделить сервис `NodeDocGenerator`, который умеет:
   - собрать контекст по правилам 8.2
   - сгенерировать `doc_tech`
   - из `doc_tech` получить `doc_public`
   - из `doc_tech` получить `doc_digest` (строгий компактный формат)
3) Добавить планировщик(и) генерации node-level документации:
   - стратегия обхода снизу вверх: METHOD → CLASS → PACKAGE → MODULE/REPO
   - не блокировать генерацию из-за отсутствия `doc_digest` зависимостей, но помечать `deps_missing`
4) Добавить refine-проход (опционально, но желательно):
   - перегенерация `doc_tech/doc_public/doc_digest` для узлов с `deps_missing=true`, когда зависимости стали доступными
5) Интеграция с `chunk`:
   - `chunk` остаётся как слой для RAG (эмбеддинги, поиск, постпроцесс)
   - `node_doc` становится каноничной документацией "на узел"
   - при необходимости: генерация RAG-чанков для "doc" может ссылаться на `node_doc.doc_public/doc_tech`

#### 8.5. Контроль размера контекста

Ввести бюджеты и top-K:
- METHOD: KDoc+sig (обязательно) + код (лимит) + digest зависимостей (top-K по EdgeKind приоритету)
- CLASS: digest методов (top-K) + рамка (KDoc)
- PACKAGE+: только digest нижнего уровня, при переполнении — предварительное сжатие

**Приоритет**: Высокий  
**Оценка времени**: 3-6 дней (с миграцией, пайплайном и минимумом интеграции)  
**Файлы для изменения/добавления** (предварительно):
- `kernel/domain/src/main/resources/liquibase/migration/08_add_node_doc_variants.sql`
- `kernel/domain/src/main/resources/liquibase/db.changelog-master.xml`
- `kernel/domain/src/main/kotlin/...` (Entity/Repository для `node_doc`)
- `contexts/chunking/impl/src/main/kotlin/...` (генератор и планировщики node-doc)

---

## Приоритизация

### Фаза 1: Критические проблемы (1-2 недели)
1. **GraphLinkerImpl** - Разделение на стратегии линковки
2. **NodeBuilder** - Выделение валидаторов и нормализаторов

### Фаза 2: Важные улучшения (1 неделя)
3. **Иерархическая генерация документации узлов** - node-level docs + digest aggregation
4. **HttpBytecodeAnalyzerImpl** - Разделение анализаторов
5. **RagServiceImpl** - Выделение поиска и контекста

### Фаза 3: Улучшения качества кода (1 неделя)
6. **ExplainRequestFactory** - Выделение построителя hints
7. **Виртуальные узлы** - Устранение дублирования
8. **Метаданные** - Типобезопасные обёртки

---

## Метрики успеха

После рефакторинга ожидаем:
- Уменьшение размера классов: максимум 200-300 строк на класс
- Уменьшение размера методов: максимум 30-40 строк на метод
- Улучшение тестируемости: каждый компонент можно тестировать изолированно
- Улучшение читаемости: четкое разделение ответственностей
- Улучшение расширяемости: легко добавлять новые типы линковки/анализа
- Появление каноничной документации "на узел" (node_doc) с `doc_public/doc_tech/doc_digest`
- Возможность агрегации документации вверх по дереву (CLASS/PACKAGE/MODULE) без раздувания исходниками

---

## Рекомендации по реализации

1. **Начать с GraphLinkerImpl** - это самый проблемный класс
2. **Использовать TDD подход** - сначала тесты, потом рефакторинг
3. **Делать маленькие коммиты** - по одному линкеру за раз
4. **Не ломать существующий функционал** - все тесты должны проходить
5. **Документировать изменения** - обновлять README и алгоритмы

---

## Дополнительные улучшения (опционально)

1. **Внедрить паттерн Chain of Responsibility** для обработки запросов в RAG
2. **Использовать Builder Pattern** для создания сложных объектов (Node, Chunk)
3. **Внедрить Event Sourcing** для отслеживания изменений графа
4. **Добавить метрики** для мониторинга производительности линковки
5. **Кэширование результатов** линковки для повторных запусков

---

**Дата создания**: 2024  
**Автор**: Команда разработки Doc-Generator

