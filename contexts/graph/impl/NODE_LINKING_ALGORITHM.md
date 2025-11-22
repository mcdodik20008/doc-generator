# Алгоритм линковки узлов графа кода

## Обзор

Данный документ описывает процесс создания рёбер (edges) между узлами графа кода. Линковка выполняется после построения всех узлов и создаёт связи между ними на основе структурных зависимостей, вызовов методов, наследования и интеграционных точек.

## Инструменты

- **NodeIndex** - индекс узлов для быстрого поиска по FQN
- **LibraryNodeIndex** - индекс узлов из библиотек для разрешения интеграционных точек
- **IntegrationPointService** - сервис для извлечения интеграционных точек из библиотек

## Основной процесс

### Фаза 1: Инициализация

**Сервис**: `GraphLinkerImpl.link()`

#### Шаг 1.1: Загрузка всех узлов

```kotlin
val all = nodeRepo.findAllByApplicationId(application.id!!, Pageable.ofSize(Int.MAX_VALUE))
```

Загружаются все узлы приложения из базы данных.

#### Шаг 1.2: Создание индекса узлов

```kotlin
val index = nodeIndexFactory.createMutable(all)
```

Создается мутабельный индекс для быстрого поиска узлов по FQN. Индекс поддерживает:
- Поиск по FQN: `findByFqn(fqn: String): Node?`
- Поиск по типу: `findByKind(kind: NodeKind): Sequence<Node>`
- Поиск по аннотациям: `findAnnotatedWith(annotation: String): Sequence<Node>`
- Разрешение типов: `resolveType(simpleOrFqn, imports, pkg): Node?`

**Алгоритм разрешения типов**:
1. Прямой поиск по FQN
2. Поиск через импорты (если имя заканчивается на `.simpleName`)
3. Поиск в текущем пакете (`$pkg.$simpleName`)
4. Поиск по простому имени (первый найденный)

### Фаза 2: Структурные связи (CONTAINS)

**Метод**: `linkContains()`

Создаёт иерархические связи "содержит":

#### Шаг 2.1: Пакет → Тип

```kotlin
all.filter { it.kind in setOf(INTERFACE, SERVICE, RECORD, MAPPER, ENDPOINT, CLASS, ENUM, CONFIG) }
    .forEach { type ->
        val pkg = index.findByFqn(type.packageName ?: return@forEach)
        res += Triple(pkg, type, EdgeKind.CONTAINS)
    }
```

Создаётся связь `PACKAGE --[CONTAINS]--> TYPE` для всех типов.

#### Шаг 2.2: Тип → Член (метод/поле)

```kotlin
all.filter { it.kind in setOf(METHOD, FIELD, ENDPOINT, JOB, TOPIC) }
    .forEach { member ->
        val ownerFqn = metaOf(member).ownerFqn
        val owner = index.findByFqn(ownerFqn)
        res += Triple(owner, member, EdgeKind.CONTAINS)
    }
```

Создаётся связь `TYPE --[CONTAINS]--> MEMBER` для всех методов и полей.

### Фаза 3: Обработка каждого узла

Для каждого узла выполняются следующие операции:

```kotlin
all.forEachIndexed { i, node ->
    val meta = metaOf(node)
    
    // Для типов
    if (node.isTypeNode()) {
        edges += linkInheritsImplements(node, meta, index)
    }
    
    // Для всех узлов
    edges += linkAnnotations(node, meta, index)
    
    // Для функций
    if (node.isFunctionNode()) {
        edges += linkSignatureDepends(node, meta, index)
        edges += linkCalls(node, meta, index)
        edges += linkIntegrationEdgesWithNodes(node, meta, index, application)
        edges += linkThrows(node, meta, index)
    }
}
```

### Фаза 4: Наследование и реализация интерфейсов

**Метод**: `linkInheritsImplements()`

#### Шаг 4.1: Извлечение супертипов

```kotlin
val candidates = (meta.supertypesResolved ?: emptyList()) + (meta.supertypesSimple ?: emptyList())
```

Собираются все супертипы из метаданных узла.

#### Шаг 4.2: Разрешение типов и создание связей

```kotlin
for (raw in candidates) {
    val target = index.resolveType(raw, imports, pkg) ?: continue
    when (target.kind) {
        NodeKind.INTERFACE -> {
            res += Triple(node, target, EdgeKind.IMPLEMENTS)
            res += Triple(node, target, EdgeKind.DEPENDS_ON)
        }
        else -> {
            res += Triple(node, target, EdgeKind.INHERITS)
            res += Triple(node, target, EdgeKind.DEPENDS_ON)
        }
    }
}
```

- Если супертип - интерфейс → `IMPLEMENTS` + `DEPENDS_ON`
- Если супертип - класс → `INHERITS` + `DEPENDS_ON`

### Фаза 5: Аннотации

**Метод**: `linkAnnotations()`

```kotlin
val annotations = meta.annotations ?: return emptyList()
for (a in annotations) {
    val t = index.resolveType(a, imports, pkg) ?: continue
    res += Triple(node, t, EdgeKind.ANNOTATED_WITH)
    res += Triple(node, t, EdgeKind.DEPENDS_ON)
}
```

Для каждой аннотации создаются две связи:
- `ANNOTATED_WITH` - узел аннотирован типом
- `DEPENDS_ON` - зависимость от типа аннотации

### Фаза 6: Зависимости из сигнатуры функции

**Метод**: `linkSignatureDepends()`

#### Шаг 6.1: Извлечение типов из сигнатуры

```kotlin
val tokens: Set<String> = when {
    !meta.paramTypes.isNullOrEmpty() || !meta.returnType.isNullOrBlank() ->
        (meta.paramTypes.orEmpty() + listOfNotNull(meta.returnType)).toSet()
    !fn.signature.isNullOrBlank() ->
        TYPE_TOKEN.findAll(fn.signature!!)
            .map { it.groupValues[1].substringBefore('<').substringBefore('?') }
            .toSet()
    else -> emptySet()
}
```

Извлекаются типы из:
1. Метаданных (`paramTypes`, `returnType`) - предпочтительно
2. Сигнатуры функции через regex `:\s*([A-Za-z_][A-Za-z0-9_\.]*)` - fallback

#### Шаг 6.2: Создание зависимостей

```kotlin
for (t in tokens) {
    val typeNode = index.resolveType(t, imports, pkg) ?: continue
    if (typeNode.id != src.id) {
        res += Triple(src, typeNode, EdgeKind.DEPENDS_ON)
    }
}
```

Создаётся связь `DEPENDS_ON` от владельца функции (класс или top-level) к типу.

### Фаза 7: Вызовы методов (CALLS)

**Метод**: `linkCalls()`

#### Шаг 7.1: Обработка простых вызовов (RawUsage.Simple)

```kotlin
is RawUsage.Simple -> {
    // Сначала пытаемся найти метод в классе-владельце
    if (owner != null) {
        index.findByFqn("${owner.fqn}.${u.name}")?.let {
            res += Triple(fn, it, EdgeKind.CALLS)
            return@forEach
        }
    }
    // Если не нашли, пытаемся разрешить как тип (конструктор)
    if (u.isCall) {
        index.resolveType(u.name, imports, pkg)?.let {
            res += Triple(fn, it, EdgeKind.CALLS)
        }
    }
}
```

Алгоритм:
1. Если есть владелец → поиск метода `{owner.fqn}.{name}`
2. Если не найдено и это вызов → разрешение как тип (конструктор)

#### Шаг 7.2: Обработка вызовов с точкой (RawUsage.Dot)

```kotlin
is RawUsage.Dot -> {
    val recvType = if (u.receiver.firstOrNull()?.isUpperCase() == true) {
        // Если начинается с заглавной - это тип
        index.resolveType(u.receiver, imports, pkg)
    } else {
        // Иначе - это переменная, используем владельца
        owner
    }
    recvType?.let { r ->
        index.findByFqn("${r.fqn}.${u.member}")?.let {
            res += Triple(fn, it, EdgeKind.CALLS)
        }
    }
}
```

Алгоритм:
1. Определение типа получателя:
   - Если `receiver` начинается с заглавной → разрешение как тип
   - Иначе → использование владельца функции
2. Поиск метода `{receiver.fqn}.{member}`
3. Создание связи `CALLS`

### Фаза 8: Интеграционные связи

**Метод**: `linkIntegrationEdgesWithNodes()`

Создаёт связи с внешними системами (HTTP endpoints, Kafka topics, Camel routes).

#### Шаг 8.1: Поиск методов библиотек

```kotlin
usages.forEach { u ->
    val libraryMethodFqn = when (u) {
        is RawUsage.Simple -> {
            if (owner != null) {
                "${owner.fqn}.${u.name}"
            } else {
                imports.firstOrNull { it.endsWith(".${u.name}") }?.let { "$it.${u.name}" }
                    ?: if (u.name.contains('.')) u.name else null
            }
        }
        is RawUsage.Dot -> {
            val recvType = if (u.receiver.firstOrNull()?.isUpperCase() == true) {
                index.resolveType(u.receiver, imports, pkg)?.fqn
            } else {
                owner?.fqn
            }
            recvType?.let { "$it.${u.member}" }
        }
    }
}
```

Строится FQN вызываемого метода библиотеки.

#### Шаг 8.2: Поиск узла библиотеки

```kotlin
if (libraryMethodFqn != null) {
    val libraryNode = libraryNodeIndex.findByMethodFqn(libraryMethodFqn)
    if (libraryNode != null) {
        val integrationPoints = integrationPointService.extractIntegrationPoints(libraryNode)
        // ...
    }
}
```

Ищется узел библиотеки и извлекаются интеграционные точки.

#### Шаг 8.3: HTTP Endpoints

```kotlin
is IntegrationPoint.HttpEndpoint -> {
    val (endpointNode, isNew) = getOrCreateEndpointNode(
        url = point.url ?: "unknown",
        httpMethod = point.httpMethod,
        index = index,
        application = application
    )
    if (endpointNode != null) {
        if (isNew) newNodes.add(endpointNode)
        res += Triple(fn, endpointNode, EdgeKind.CALLS_HTTP)
        
        // Дополнительные связи для resilience patterns
        if (point.hasRetry) {
            res += Triple(fn, endpointNode, EdgeKind.RETRIES_TO)
        }
        if (point.hasTimeout) {
            res += Triple(fn, endpointNode, EdgeKind.TIMEOUTS_TO)
        }
        if (point.hasCircuitBreaker) {
            res += Triple(fn, endpointNode, EdgeKind.CIRCUIT_BREAKER_TO)
        }
    }
}
```

**FQN endpoint узла**: `endpoint://{httpMethod} {url}`

Создаются связи:
- `CALLS_HTTP` - основной вызов
- `RETRIES_TO` - если есть retry логика
- `TIMEOUTS_TO` - если есть timeout
- `CIRCUIT_BREAKER_TO` - если есть circuit breaker

#### Шаг 8.4: Kafka Topics

```kotlin
is IntegrationPoint.KafkaTopic -> {
    val (topicNode, isNew) = getOrCreateTopicNode(
        topic = point.topic ?: "unknown",
        index = index,
        application = application
    )
    if (topicNode != null) {
        if (isNew) newNodes.add(topicNode)
        when (point.operation) {
            "PRODUCE" -> res += Triple(fn, topicNode, EdgeKind.PRODUCES)
            "CONSUME" -> res += Triple(fn, topicNode, EdgeKind.CONSUMES)
        }
    }
}
```

**FQN topic узла**: `topic://{topic}`

Создаются связи:
- `PRODUCES` - если метод публикует в topic
- `CONSUMES` - если метод читает из topic

#### Шаг 8.5: Camel Routes

```kotlin
is IntegrationPoint.CamelRoute -> {
    val (endpointNode, isNew) = getOrCreateEndpointNode(
        url = point.uri ?: "unknown",
        httpMethod = null,
        index = index,
        application = application
    )
    if (endpointNode != null) {
        if (isNew) newNodes.add(endpointNode)
        if (point.endpointType == "http" || point.uri?.startsWith("http") == true) {
            res += Triple(fn, endpointNode, EdgeKind.CALLS_HTTP)
        }
    }
}
```

Для Camel routes создаются endpoint узлы аналогично HTTP.

### Фаза 9: Исключения (THROWS)

**Метод**: `linkThrows()`

```kotlin
val throwsTypes = meta.throwsTypes ?: return emptyList()
throwsTypes.forEach { throwType ->
    index.resolveType(throwType, imports, pkg)?.let {
        res += Triple(fn, it, EdgeKind.THROWS)
    }
}
```

Для каждого типа исключения создаётся связь `THROWS` от функции к типу исключения.

### Фаза 10: Обновление индекса новыми узлами

```kotlin
if (newlyCreatedNodes.isNotEmpty()) {
    log.info("Updating index with ${newlyCreatedNodes.size} newly created nodes (ENDPOINT/TOPIC)")
    if (index is NodeIndexFactory.MutableNodeIndex) {
        index.addNodes(newlyCreatedNodes)
    }
}
```

Виртуальные узлы (ENDPOINT, TOPIC), созданные во время линковки, добавляются в индекс для последующих операций.

### Фаза 11: Сохранение рёбер в БД

**Сервис**: `GraphSinkDb.upsertEdges()`

```kotlin
sink.upsertEdges(
    edges.asSequence().map { (src, dst, kind) ->
        SimpleEdgeProposal(kind, src, dst)
    }
)
```

#### Шаг 11.1: Преобразование в EdgeProposal

Каждая тройка `(src, dst, kind)` преобразуется в `EdgeProposal`.

#### Шаг 11.2: Сохранение через репозиторий

```kotlin
edges.forEach { e ->
    val srcId = e.source.id
    val dstId = e.target.id
    if (srcId != null && dstId != null) {
        edgeRepository.upsert(srcId, dstId, e.kind.name)
        written++
    } else {
        skipped++  // Узел без ID пропускается
    }
}
```

Рёбра сохраняются в БД через `EdgeRepository.upsert()`.

## Типы связей (EdgeKind)

### Структурные связи

- **CONTAINS** - иерархическая связь (пакет → тип, тип → член)
- **DEPENDS_ON** - зависимость от типа (импорт, использование в сигнатуре)
- **IMPLEMENTS** - реализация интерфейса
- **INHERITS** - наследование класса
- **ANNOTATED_WITH** - аннотация узла

### Вызовы в коде

- **CALLS** - вызов метода/конструктора
- **THROWS** - выбрасывание исключения

### Интеграционные связи

- **CALLS_HTTP** - HTTP вызов endpoint
- **PRODUCES** - публикация в Kafka topic
- **CONSUMES** - чтение из Kafka topic
- **RETRIES_TO** - retry логика для endpoint
- **TIMEOUTS_TO** - timeout для endpoint
- **CIRCUIT_BREAKER_TO** - circuit breaker для endpoint

## Структура Edge

```kotlin
data class Edge(
    val src: Node,              // Исходный узел
    val dst: Node,              // Целевой узел
    val kind: EdgeKind,         // Тип связи
    val evidence: Map<String, Any>,  // Доказательства (JSONB)
    val explainMd: String?,    // LLM-трактовка связи (Markdown)
    val confidence: BigDecimal?, // Уверенность 0.00-1.00
    val relationStrength: String? // weak|normal|strong
)
```

**Составной ключ**: `(src_id, dst_id, kind)` - уникальная комбинация.

## NodeIndex - алгоритм разрешения типов

### Метод resolveType()

```kotlin
fun resolveType(
    simpleOrFqn: String,
    imports: List<String>,
    pkg: String
): Node? {
    // 1. Прямой поиск по FQN
    byFqn[simpleOrFqn]?.let { return it }
    
    // 2. Извлечение простого имени
    val simple = simpleOrFqn
        .substringAfterLast('.')
        .removeSuffix("?")  // Убираем nullable
        .substringBefore('<')  // Убираем generics
    
    // 3. Поиск через импорты
    imports.firstOrNull { it.endsWith(".$simple") }?.let { 
        byFqn[it] 
    }?.let { return it }
    
    // 4. Поиск в текущем пакете
    byFqn["$pkg.$simple"]?.let { return it }
    
    // 5. Поиск по простому имени (первый найденный)
    return bySimple[simple]?.firstOrNull()
}
```

**Приоритет разрешения**:
1. Полный FQN
2. Импорт с совпадающим простым именем
3. Текущий пакет
4. Простое имя (первый найденный)

## Виртуальные узлы

### ENDPOINT узел

Создаётся для HTTP endpoints и Camel routes:

```kotlin
Node(
    fqn = "endpoint://$httpMethod $url",  // или "endpoint://$url"
    name = url.substringAfterLast('/'),
    kind = NodeKind.ENDPOINT,
    lang = Lang.java,  // виртуальный узел
    meta = mapOf(
        "url" to url,
        "httpMethod" to (httpMethod ?: "UNKNOWN"),
        "source" to "library_analysis"
    )
)
```

### TOPIC узел

Создаётся для Kafka topics:

```kotlin
Node(
    fqn = "topic://$topic",
    name = topic,
    kind = NodeKind.TOPIC,
    lang = Lang.java,  // виртуальный узел
    meta = mapOf(
        "topic" to topic,
        "source" to "library_analysis"
    )
)
```

## Схема потока данных

```
GraphLinkerImpl.link()
  ↓
Загрузка всех узлов из БД
  ↓
Создание NodeIndex (мутабельный)
  ↓
linkContains() - структурные связи
  ↓
Для каждого узла:
  ├─→ linkInheritsImplements() - наследование/реализация
  ├─→ linkAnnotations() - аннотации
  └─→ Для функций:
      ├─→ linkSignatureDepends() - зависимости из сигнатуры
      ├─→ linkCalls() - вызовы методов
      ├─→ linkIntegrationEdgesWithNodes() - интеграционные связи
      │   ├─→ Поиск в LibraryNodeIndex
      │   ├─→ Извлечение IntegrationPoints
      │   ├─→ Создание виртуальных узлов (ENDPOINT/TOPIC)
      │   └─→ Создание интеграционных рёбер
      └─→ linkThrows() - исключения
  ↓
Обновление индекса новыми узлами
  ↓
GraphSinkDb.upsertEdges()
  ├─→ Преобразование в EdgeProposal
  └─→ Сохранение в БД через EdgeRepository
```

## Оптимизации

1. **Индекс узлов**: Быстрый поиск по FQN через HashMap
2. **Мутабельный индекс**: Поддержка добавления новых узлов во время линковки
3. **Кэширование разрешений**: Повторное использование результатов `resolveType()`
4. **Батчинг**: Сохранение рёбер последовательно, но в одной транзакции

## Обработка ошибок

1. **Ошибка при linkCalls**: Логируется, но не прерывает процесс
2. **Узел без ID**: Пропускается при сохранении рёбер
3. **Ошибка создания виртуального узла**: Логируется предупреждение, узел не создаётся
4. **Ошибка сохранения ребра**: Логируется ошибка, счётчик ошибок увеличивается

## Статистика

После линковки логируется:
- Количество обработанных узлов
- Количество ошибок при linkCalls
- Количество созданных виртуальных узлов (ENDPOINT/TOPIC)
- Количество созданных рёбер

## Связанные компоненты

- `NodeIndexFactory` - создание индексов узлов
- `LibraryNodeIndex` - индекс узлов из библиотек
- `IntegrationPointService` - извлечение интеграционных точек
- `GraphSink` - сохранение рёбер в БД
- `EdgeRepository` - доступ к БД для рёбер

