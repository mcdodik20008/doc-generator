# План рефакторинга архитектурно не проработанных участков

## Обзор

Данный документ описывает выявленные архитектурные проблемы и план их решения через рефакторинг.

## Выявленные проблемы

### 1. GraphLinkerImpl - Нарушение Single Responsibility Principle

**Проблема**: Класс `GraphLinkerImpl` (636 строк) содержит слишком много ответственностей:
- Линковка структурных связей (CONTAINS)
- Линковка наследования и реализации (INHERITS, IMPLEMENTS)
- Линковка аннотаций (ANNOTATED_WITH)
- Линковка зависимостей из сигнатуры (DEPENDS_ON)
- Линковка вызовов методов (CALLS)
- Линковка исключений (THROWS)
- Линковка интеграционных связей (HTTP, Kafka, Camel)
- Создание виртуальных узлов (ENDPOINT, TOPIC)

**Текущая структура**:
```kotlin
class GraphLinkerImpl {
    fun link() // Главный метод - 70 строк
    private fun linkContains() // 40 строк
    private fun linkInheritsImplements() // 25 строк
    private fun linkAnnotations() // 15 строк
    private fun linkSignatureDepends() // 30 строк
    private fun linkCalls() // 40 строк
    private fun linkThrows() // 15 строк
    private fun linkIntegrationEdgesWithNodes() // 200+ строк
    private fun getOrCreateEndpointNode() // 50 строк
    private fun getOrCreateTopicNode() // 40 строк
}
```

**План рефакторинга**:

1. **Создать стратегию линковки (Strategy Pattern)**:
   ```kotlin
   interface EdgeLinker {
       fun link(node: Node, meta: NodeMeta, index: NodeIndex): List<Triple<Node, Node, EdgeKind>>
   }
   ```

2. **Выделить отдельные линкеры**:
   - `StructuralEdgeLinker` - CONTAINS связи
   - `InheritanceEdgeLinker` - INHERITS, IMPLEMENTS
   - `AnnotationEdgeLinker` - ANNOTATED_WITH
   - `SignatureDependencyLinker` - DEPENDS_ON из сигнатуры
   - `CallEdgeLinker` - CALLS
   - `ThrowEdgeLinker` - THROWS
   - `IntegrationEdgeLinker` - интеграционные связи (HTTP, Kafka, Camel)

3. **Выделить фабрику виртуальных узлов**:
   ```kotlin
   interface VirtualNodeFactory {
       fun getOrCreateEndpointNode(url: String, httpMethod: String?, ...): Pair<Node?, Boolean>
       fun getOrCreateTopicNode(topic: String, ...): Pair<Node?, Boolean>
   }
   ```

4. **Упростить GraphLinkerImpl**:
   - Оставить только оркестрацию
   - Использовать список линкеров через dependency injection
   - Убрать всю бизнес-логику в специализированные классы

**Приоритет**: 🔴 Высокий  
**Оценка времени**: 2-3 дня  
**Файлы для изменения**:
- `contexts/graph/impl/src/main/kotlin/com/bftcom/docgenerator/graph/impl/linker/GraphLinkerImpl.kt`
- Создать новые файлы для каждого линкера

---

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

**Приоритет**: 🟡 Средний  
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

**Приоритет**: 🟡 Средний  
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

**Приоритет**: 🟢 Низкий  
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

**Приоритет**: 🟡 Средний  
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

**Приоритет**: 🟢 Низкий  
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

**Приоритет**: 🟢 Низкий  
**Оценка времени**: 0.5 дня  
**Файлы для изменения**:
- `contexts/graph/impl/src/main/kotlin/com/bftcom/docgenerator/graph/impl/linker/GraphLinkerImpl.kt`

---

## Приоритизация

### Фаза 1: Критические проблемы (1-2 недели)
1. ✅ **GraphLinkerImpl** - Разделение на стратегии линковки
2. ✅ **NodeBuilder** - Выделение валидаторов и нормализаторов

### Фаза 2: Важные улучшения (1 неделя)
3. ✅ **HttpBytecodeAnalyzerImpl** - Разделение анализаторов
4. ✅ **RagServiceImpl** - Выделение поиска и контекста

### Фаза 3: Улучшения качества кода (1 неделя)
5. ✅ **ExplainRequestFactory** - Выделение построителя hints
6. ✅ **Виртуальные узлы** - Устранение дублирования
7. ✅ **Метаданные** - Типобезопасные обёртки

---

## Метрики успеха

После рефакторинга ожидаем:
- ✅ Уменьшение размера классов: максимум 200-300 строк на класс
- ✅ Уменьшение размера методов: максимум 30-40 строк на метод
- ✅ Улучшение тестируемости: каждый компонент можно тестировать изолированно
- ✅ Улучшение читаемости: четкое разделение ответственностей
- ✅ Улучшение расширяемости: легко добавлять новые типы линковки/анализа

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

