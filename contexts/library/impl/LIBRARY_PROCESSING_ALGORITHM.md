# Алгоритм обработки библиотек

## Обзор

Данный документ описывает процесс анализа библиотек (JAR-файлов) из classpath, извлечения их структуры через байткод-анализ и создания узлов графа для последующего использования при линковке интеграционных связей.

## Инструменты

- **ASM** (`org.objectweb.asm`) - библиотека для анализа байткода Java/Kotlin
- **JarFile** - работа с JAR-архивами
- **LibraryCoordinateParser** - извлечение координат библиотеки (groupId:artifactId:version)

## Основной процесс

### Фаза 1: Инициализация обработки

**Сервис**: `LibraryBuildEventListener.onLibraryBuildRequested()`

**Событие**: `LibraryBuildRequestedEvent` содержит:
- `applicationId` - ID приложения
- `sourceRoot` - корневая директория исходников
- `classpath` - список JAR-файлов зависимостей

#### Шаг 1.1: Запуск обработки

```kotlin
val result = libraryBuilder.buildLibraries(event.classpath)
```

### Фаза 2: Обработка всех JAR-файлов

**Сервис**: `LibraryBuilderImpl.buildLibraries()`

#### Шаг 2.1: Фильтрация JAR-файлов

```kotlin
val jarFiles = classpath.filter { it.name.endsWith(".jar", ignoreCase = true) }
```

Из classpath извлекаются только JAR-файлы.

#### Шаг 2.2: Обработка каждого JAR

```kotlin
for (jarFile in jarFiles) {
    try {
        val result = self?.processSingleLibrary(jarFile)
        librariesProcessed += result.librariesProcessed
        librariesSkipped += result.librariesSkipped
        nodesCreated += result.nodesCreated
    } catch (e: Exception) {
        errors.add("Failed to process jar ${jarFile.name}: ${e.message}")
    }
}
```

Каждый JAR обрабатывается в отдельной транзакции (`@Transactional(REQUIRES_NEW)`).

### Фаза 3: Обработка одной библиотеки

**Метод**: `LibraryBuilderImpl.processSingleLibrary()`

#### Шаг 3.1: Извлечение координат библиотеки

```kotlin
val coordinate = coordinateParser.parseCoordinate(jarFile)
if (coordinate == null) {
    return SingleLibraryResult() // Пропускаем JAR без координат
}
```

**Алгоритм извлечения координат** (`LibraryCoordinateParser`):

1. **Поиск в pom.properties**:
   - Путь: `META-INF/maven/{groupId}/{artifactId}/pom.properties`
   - Чтение свойств: `groupId`, `artifactId`, `version`
   - Формат координаты: `{groupId}:{artifactId}:{version}`

2. **Fallback: извлечение из имени файла**:
   - Парсинг: `artifactId-version.jar`
   - Regex: `-(\d+(\.\d+).*)$` для поиска версии
   - Восстановление groupId из пути:
     - Стандартная структура Maven: `.../group/id/parts/artifactId/version/file.jar`
     - Остановка на маркерах: `repository`, `libs`, `.m2`, `.gradle`, `caches`, `maven`

#### Шаг 3.2: Проверка принадлежности компании

```kotlin
if (!isCompanyLibrary(coordinate)) {
    return SingleLibraryResult(librariesSkipped = 1)
}
```

**Whitelist префиксов**:
- `com.bftcom`
- `ru.bftcom`
- `ru.supercode`
- `rrbpm`

Обрабатываются только библиотеки компании.

#### Шаг 3.3: Проверка существования библиотеки

```kotlin
val existingLibrary = libraryRepo.findByCoordinate(coordinate.coordinate)
if (existingLibrary != null) {
    return SingleLibraryResult(librariesSkipped = 1)
}
```

Если библиотека уже обработана, пропускаем её.

#### Шаг 3.4: Создание записи Library

```kotlin
val library = Library(
    coordinate = coordinate.coordinate,
    groupId = coordinate.groupId,
    artifactId = coordinate.artifactId,
    version = coordinate.version,
    kind = determineLibraryKind(coordinate),
    metadata = emptyMap()
)
libraryRepo.save(library)
```

**Определение типа библиотеки**:
- `framework` - если `groupId.startsWith("org.springframework")`
- `library` - если `groupId.startsWith("com.fasterxml.jackson")`
- `language` - если `groupId.startsWith("org.jetbrains.kotlin")`
- `company` - если в whitelist
- `external` - иначе

### Фаза 4: Парсинг байткода

**Сервис**: `BytecodeParserImpl.parseJar()`

#### Шаг 4.1: Открытие JAR-архива

```kotlin
JarFile(jarFile).use { jar ->
    val classEntries = mutableListOf<ZipEntry>()
    val entriesEnum = jar.entries()
    while (entriesEnum.hasMoreElements()) {
        val entry = entriesEnum.nextElement()
        if (!entry.isDirectory && entry.name.endsWith(".class")) {
            classEntries += entry
        }
    }
}
```

Собираются все `.class` файлы из JAR.

#### Шаг 4.2: Парсинг каждого класса через ASM

```kotlin
for (entry in classEntries) {
    jar.getInputStream(entry).use { input ->
        parseClass(input, entry.name, nodes)
    }
}
```

**Метод `parseClass()`**:
```kotlin
val reader = ClassReader(input.readBytes())
val visitor = LibraryClassVisitor(filePath, nodes)
reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
```

**Важно**: Используется `SKIP_CODE` - тело методов не анализируется на этом этапе.

#### Шаг 4.3: Извлечение информации о классе

**ClassVisitor.visit()**:
```kotlin
override fun visit(version, access, name, signature, superName, interfaces) {
    internalName = name  // org/example/MyClass
    classFqn = name.replace('/', '.')  // org.example.MyClass
    simpleName = classFqn.substringAfterLast('.')
    packageName = classFqn.substringBeforeLast('.')
    classModifiers = extractModifiers(access)
    superClassFqn = superName?.replace('/', '.')
    interfaceFqns = interfaces?.map { it.replace('/', '.') }
}
```

**Извлечение модификаторов**:
- `ACC_PUBLIC` → `"public"`
- `ACC_PRIVATE` → `"private"`
- `ACC_PROTECTED` → `"protected"`
- `ACC_STATIC` → `"static"`
- `ACC_FINAL` → `"final"`
- `ACC_ABSTRACT` → `"abstract"`
- `ACC_INTERFACE` → `"interface"`
- `ACC_ENUM` → `"enum"`

**Определение типа класса**:
- Если `"enum" in modifiers` → `NodeKind.ENUM`
- Если `"interface" in modifiers` → `NodeKind.INTERFACE`
- Иначе → `NodeKind.CLASS`

#### Шаг 4.4: Извлечение полей

**ClassVisitor.visitField()**:
```kotlin
override fun visitField(access, name, descriptor, signature, value) {
    val ownerFqn = classFqn
    val fieldFqn = "$ownerFqn.$name"
    val fieldType = Type.getType(descriptor).className
    
    nodes.add(RawLibraryNode(
        fqn = fieldFqn,
        name = name,
        packageName = packageName,
        kind = NodeKind.FIELD,
        lang = Lang.java,
        filePath = filePath,
        signature = fieldType,
        annotations = emptyList(),
        modifiers = extractModifiers(access),
        parentFqn = ownerFqn,
        meta = mapOf(
            "descriptor" to descriptor,
            "type" to fieldType,
            "signature" to signature,
            "initialValue" to value
        )
    ))
}
```

#### Шаг 4.5: Извлечение методов

**ClassVisitor.visitMethod()**:
```kotlin
override fun visitMethod(access, name, descriptor, signature, exceptions) {
    if (name == "<clinit>") return null  // Пропускаем статические инициализаторы
    
    val ownerFqn = classFqn
    val methodFqn = "$ownerFqn.$name"
    val methodSignature = buildMethodSignature(name, descriptor)
    
    nodes.add(RawLibraryNode(
        fqn = methodFqn,
        name = name,
        packageName = packageName,
        kind = NodeKind.METHOD,
        lang = Lang.java,
        filePath = filePath,
        signature = methodSignature,
        annotations = emptyList(),
        modifiers = extractModifiers(access),
        parentFqn = ownerFqn,
        meta = mapOf(
            "descriptor" to descriptor,
            "signature" to signature,
            "exceptions" to exceptions?.map { it.replace('/', '.') },
            "kotlin_suspend" to isSuspendMethod(descriptor),
            "synthetic_coroutine_helper" to isSyntheticCoroutineHelper(name, access)
        )
    ))
}
```

**Построение сигнатуры метода**:
```kotlin
fun buildMethodSignature(name: String, descriptor: String): String {
    val methodType = Type.getMethodType(descriptor)
    val params = methodType.argumentTypes.joinToString(", ") { it.className }
    val returnType = methodType.returnType.className
    return "$name($params): $returnType"
}
```

**Определение Kotlin suspend функций**:
- Проверка последнего параметра: `kotlin.coroutines.Continuation`
- Проверка возвращаемого типа: `java.lang.Object`

### Фаза 5: Анализ интеграционных вызовов

**Сервис**: `HttpBytecodeAnalyzerImpl.analyzeJar()`

**Важно**: Этот анализ выполняется **с анализом кода** (`ClassReader` без `SKIP_CODE`).

#### Шаг 5.1: Построение call graph

```kotlin
val calls = mutableMapOf<MethodId, MutableSet<MethodId>>()
val methodAccessFlags = mutableMapOf<MethodId, Int>()
val httpCallSites = mutableListOf<HttpCallSite>()
val kafkaCallSites = mutableListOf<KafkaCallSite>()
val camelCallSites = mutableListOf<CamelCallSite>()

for (entry in classEntries) {
    analyzeClass(input, calls, methodAccessFlags, httpCallSites, kafkaCallSites, camelCallSites)
}
```

#### Шаг 5.2: Обнаружение HTTP-вызовов

**Паттерны HTTP-клиентов**:
- **WebClient**: `org.springframework.web.reactive.function.client.WebClient*`
- **RestTemplate**: `org.springframework.web.client.RestTemplate`
- **OkHttp**: `okhttp3.OkHttpClient`
- **Apache HttpClient**: `org.apache.http.client.HttpClient`

**Алгоритм обнаружения**:
1. Отслеживание вызовов методов HTTP-клиентов
2. Извлечение URL из стека операндов:
   - Анализ `StringBuilder.append()` и `toString()`
   - Анализ `String.concat()`
   - Анализ `LDC` (load constant) инструкций
3. Определение HTTP-метода по имени метода:
   - `get()` → `GET`
   - `post()` → `POST`
   - `put()` → `PUT`
   - `delete()` → `DELETE`
   - и т.д.

**Обнаружение resilience patterns**:
- `retry()` → `hasRetry = true`
- `timeout()` → `hasTimeout = true`
- Проверка наличия Circuit Breaker библиотек

#### Шаг 5.3: Обнаружение Kafka-вызовов

**Паттерны Kafka-клиентов**:
- `org.apache.kafka.clients.producer.KafkaProducer`
- `org.apache.kafka.clients.consumer.KafkaConsumer`

**Извлечение информации**:
- Topic из параметров методов `send()` / `poll()`
- Operation: `PRODUCE` или `CONSUME`
- Client type: `KafkaProducer` или `KafkaConsumer`

#### Шаг 5.4: Обнаружение Camel-вызовов

**Паттерны Camel**:
- `org.apache.camel.builder.RouteBuilder`
- `org.apache.camel.ProducerTemplate`
- `org.apache.camel.ConsumerTemplate`

**Извлечение информации**:
- URI из параметров методов
- Endpoint type: `http`, `kafka`, `jms`, и т.д.
- Direction: `from` или `to`

#### Шаг 5.5: Построение call graph и подъем до родительских клиентов

```kotlin
val parentClients = findParentClients(calls, httpCallSites, methodAccessFlags)
```

**Алгоритм**:
1. Построение графа вызовов из `calls`
2. Поиск публичных методов, которые вызывают HTTP/Kafka/Camel методы
3. Подъем информации о вызовах до родительских методов
4. Маркировка родительских методов как `isParentClient = true`

### Фаза 6: Фильтрация релевантных узлов

**Метод**: `saveLibraryNodes()`

#### Шаг 6.1: Фильтрация классов

```kotlin
val relevantClassNodes = allClassNodes.filter { raw ->
    isIntegrationRelevantClass(raw)
}
```

**Критерии релевантности класса**:
1. **Публичность**: `"public" in modifiers`
2. **Интеграционные аннотации**:
   - `RestController`, `Controller`
   - `FeignClient`
   - `KafkaListener`, `RabbitListener`
   - `Service`, `Component`
   - `WebClient`
3. **HTTP-клиенты** (по FQN):
   - `webclient`, `resttemplate`, `okhttp`, `httpclient`, `feign`, `restclient`
4. **Kafka-клиенты**:
   - `kafkaproducer`, `kafkaconsumer`, `kafka`
5. **Camel**:
   - `routebuilder`, `camel`
6. **Интерфейсы**: Все интерфейсы (могут быть контрактами)

#### Шаг 6.2: Фильтрация методов и полей

```kotlin
val relevantMemberNodes = allMemberNodes.filter { raw ->
    isIntegrationRelevantMember(raw, relevantClassFqns, integrationMethods, analysisResult)
}
```

**Критерии релевантности метода**:
1. **Публичность**: `"public" in modifiers`
2. **Интеграционные аннотации**:
   - `GetMapping`, `PostMapping`, `PutMapping`, `DeleteMapping`, `PatchMapping`
   - `RequestMapping`
   - `KafkaListener`, `RabbitListener`
   - `Scheduled`, `EventListener`
3. **Интеграционные вызовы**: Метод в `integrationMethods` (из анализа байткода)
4. **Методы интеграционных классов**: Публичные методы классов из `relevantClassFqns`

**Критерии релевантности поля**:
1. **Публичные поля интеграционных классов**
2. **Статические константы интеграционных классов**: `"static" in modifiers && "final" in modifiers`

### Фаза 7: Сохранение LibraryNode

#### Шаг 7.1: Сохранение классов

```kotlin
for (raw in relevantClassNodes) {
    val existing = libraryNodeRepo.findByLibraryIdAndFqn(library.id!!, raw.fqn)
    if (existing == null) {
        val node = createLibraryNode(library, raw, null, analysisResult)
        val savedNode = libraryNodeRepo.save(node)
        parentMap[raw.fqn] = savedNode
        saved++
    } else {
        parentMap[raw.fqn] = existing
    }
}
```

#### Шаг 7.2: Сохранение методов и полей

```kotlin
for (raw in relevantMemberNodes) {
    val existing = libraryNodeRepo.findByLibraryIdAndFqn(library.id!!, raw.fqn)
    if (existing == null) {
        val parent = raw.parentFqn?.let { parentMap[it] }
        val node = createLibraryNode(library, raw, parent, analysisResult)
        libraryNodeRepo.save(node)
        saved++
    }
}
```

#### Шаг 7.3: Создание LibraryNode с интеграционными метаданными

**Метод**: `createLibraryNode()`

```kotlin
val metaMap = mutableMapOf<String, Any>(
    "annotations" to raw.annotations,
    "modifiers" to raw.modifiers.toList()
)
metaMap.putAll(raw.meta)

// Для методов добавляем интеграционный анализ
if (raw.kind == NodeKind.METHOD && analysisResult != null) {
    val methodSummary = findMethodSummary(raw.fqn, analysisResult)
    if (methodSummary != null) {
        val integrationMeta = mutableMapOf<String, Any>()
        
        if (methodSummary.isParentClient) {
            integrationMeta["isParentClient"] = true
        }
        
        // HTTP
        if (methodSummary.urls.isNotEmpty()) {
            integrationMeta["urls"] = methodSummary.urls.toList()
        }
        if (methodSummary.httpMethods.isNotEmpty()) {
            integrationMeta["httpMethods"] = methodSummary.httpMethods.toList()
        }
        if (methodSummary.hasRetry) {
            integrationMeta["hasRetry"] = true
        }
        if (methodSummary.hasTimeout) {
            integrationMeta["hasTimeout"] = true
        }
        if (methodSummary.hasCircuitBreaker) {
            integrationMeta["hasCircuitBreaker"] = true
        }
        
        // Kafka
        if (methodSummary.kafkaTopics.isNotEmpty()) {
            integrationMeta["kafkaTopics"] = methodSummary.kafkaTopics.toList()
        }
        if (methodSummary.directKafkaCalls.isNotEmpty()) {
            integrationMeta["kafkaCalls"] = methodSummary.directKafkaCalls.map { ... }
        }
        
        // Camel
        if (methodSummary.camelUris.isNotEmpty()) {
            integrationMeta["camelUris"] = methodSummary.camelUris.toList()
        }
        if (methodSummary.directCamelCalls.isNotEmpty()) {
            integrationMeta["camelCalls"] = methodSummary.directCamelCalls.map { ... }
        }
        
        if (integrationMeta.isNotEmpty()) {
            metaMap["integrationAnalysis"] = integrationMeta
        }
    }
}

return LibraryNode(
    library = library,
    fqn = raw.fqn,
    name = raw.name,
    packageName = raw.packageName,
    kind = raw.kind,
    lang = raw.lang,
    parent = parent,
    filePath = raw.filePath,
    signature = raw.signature,
    meta = metaMap
)
```

### Фаза 8: Построение индекса LibraryNode

**Сервис**: `LibraryNodeIndexImpl.buildIndex()`

**Вызывается**: При старте приложения через `@PostConstruct`

#### Шаг 8.1: Загрузка всех узлов

```kotlin
val allNodes = libraryNodeRepo.findAll()
```

#### Шаг 8.2: Индексация методов

```kotlin
for (node in allNodes) {
    if (node.kind.name == "METHOD") {
        // Индекс по полному FQN метода
        byMethodFqn[node.fqn] = node
        
        // Индекс по классу и методу
        val lastDot = node.fqn.lastIndexOf('.')
        if (lastDot > 0) {
            val classFqn = node.fqn.substring(0, lastDot)
            val methodName = node.fqn.substring(lastDot + 1)
            val key = "$classFqn.$methodName"
            byClassAndMethod[key] = node
        }
    }
}
```

**Индексы**:
- `byMethodFqn` - поиск по полному FQN метода: `com.example.Class.method`
- `byClassAndMethod` - поиск по классу и имени метода: `com.example.Class.methodName`

### Фаза 9: Извлечение интеграционных точек

**Сервис**: `IntegrationPointServiceImpl.extractIntegrationPoints()`

Используется при линковке графа для создания интеграционных рёбер.

#### Шаг 9.1: Извлечение метаданных

```kotlin
val integrationMeta = meta["integrationAnalysis"] as? Map<String, Any> ?: return emptyList()
```

#### Шаг 9.2: Создание HTTP endpoints

```kotlin
val urls = integrationMeta["urls"] as? List<String> ?: emptyList()
val httpMethods = integrationMeta["httpMethods"] as? List<String> ?: emptyList()

for (url in urls) {
    for (httpMethod in httpMethods.ifEmpty { listOf(null) }) {
        points.add(IntegrationPoint.HttpEndpoint(
            url = url,
            methodId = methodFqn,
            httpMethod = httpMethod,
            hasRetry = hasRetry,
            hasTimeout = hasTimeout,
            hasCircuitBreaker = hasCircuitBreaker
        ))
    }
}
```

#### Шаг 9.3: Создание Kafka topics

```kotlin
val kafkaTopics = integrationMeta["kafkaTopics"] as? List<String> ?: emptyList()
val kafkaCalls = integrationMeta["kafkaCalls"] as? List<Map<String, Any>> ?: emptyList()

for (topic in kafkaTopics) {
    val call = kafkaCalls.firstOrNull { it["topic"] == topic }
    points.add(IntegrationPoint.KafkaTopic(
        methodId = methodFqn,
        topic = topic,
        operation = call?.get("operation") ?: "UNKNOWN",
        clientType = call?.get("clientType") ?: "Unknown"
    ))
}
```

#### Шаг 9.4: Создание Camel routes

```kotlin
val camelUris = integrationMeta["camelUris"] as? List<String> ?: emptyList()
val camelCalls = integrationMeta["camelCalls"] as? List<Map<String, Any>> ?: emptyList()

for (uri in camelUris) {
    val call = camelCalls.firstOrNull { it["uri"] == uri }
    points.add(IntegrationPoint.CamelRoute(
        methodId = methodFqn,
        uri = uri,
        endpointType = call?.get("endpointType"),
        direction = call?.get("direction") ?: "UNKNOWN"
    ))
}
```

## Структура LibraryNode

```kotlin
data class LibraryNode(
    val id: Long?,
    val library: Library,           // Ссылка на библиотеку
    val fqn: String,                // Fully Qualified Name
    val name: String,               // Простое имя
    val packageName: String?,       // Пакет
    val kind: NodeKind,             // CLASS, INTERFACE, METHOD, FIELD, etc.
    val lang: Lang,                 // Язык (обычно JAVA для байткода)
    val parent: LibraryNode?,       // Родительский узел (класс для метода/поля)
    val filePath: String?,          // Путь к .class файлу в JAR
    val signature: String?,         // Сигнатура (для методов)
    val meta: Map<String, Any>      // Метаданные, включая integrationAnalysis
)
```

## Структура Library

```kotlin
data class Library(
    val id: Long?,
    val coordinate: String,         // groupId:artifactId:version
    val groupId: String,
    val artifactId: String,
    val version: String,
    val kind: String,               // framework, library, language, company, external
    val metadata: Map<String, Any>
)
```

## Схема потока данных

```
LibraryBuildRequestedEvent
  ↓
LibraryBuilderImpl.buildLibraries()
  ├─→ Фильтрация JAR-файлов
  └─→ Для каждого JAR:
      ├─→ LibraryCoordinateParser.parseCoordinate()
      │   ├─→ Поиск в pom.properties
      │   └─→ Fallback: извлечение из имени файла
      │
      ├─→ Проверка isCompanyLibrary()
      │
      ├─→ Проверка существования библиотеки
      │
      ├─→ Создание Library
      │
      ├─→ BytecodeParserImpl.parseJar()
      │   ├─→ Открытие JAR-архива
      │   ├─→ Парсинг .class файлов через ASM
      │   ├─→ Извлечение классов, методов, полей
      │   └─→ Создание RawLibraryNode
      │
      ├─→ HttpBytecodeAnalyzerImpl.analyzeJar()
      │   ├─→ Анализ кода методов (без SKIP_CODE)
      │   ├─→ Обнаружение HTTP/Kafka/Camel вызовов
      │   ├─→ Построение call graph
      │   └─→ Подъем до родительских клиентов
      │
      └─→ saveLibraryNodes()
          ├─→ Фильтрация релевантных узлов
          ├─→ Сохранение классов
          ├─→ Сохранение методов/полей
          └─→ Добавление integrationAnalysis в meta
              ↓
LibraryNodeIndexImpl.buildIndex() (при старте)
  ├─→ Загрузка всех LibraryNode
  └─→ Индексация по FQN метода
      ↓
Использование при линковке:
  ├─→ LibraryNodeIndex.findByMethodFqn()
  └─→ IntegrationPointService.extractIntegrationPoints()
```

## Оптимизации

1. **Транзакции**: Каждая библиотека обрабатывается в отдельной транзакции
2. **Фильтрация**: Сохраняются только релевантные для интеграции узлы
3. **Индексация**: Быстрый поиск методов по FQN через HashMap
4. **Кэширование**: Индекс строится один раз при старте приложения

## Обработка ошибок

1. **JAR без координат**: Пропускается с логированием
2. **Внешняя библиотека**: Пропускается (не в whitelist)
3. **Ошибка парсинга класса**: Логируется, класс пропускается
4. **Ошибка анализа байткода**: Логируется предупреждение, анализ пропускается
5. **Ошибка сохранения**: Логируется, библиотека помечается как ошибка

## Статистика

После обработки возвращается `LibraryBuildResult`:
- `librariesProcessed` - количество обработанных библиотек
- `librariesSkipped` - количество пропущенных библиотек
- `nodesCreated` - количество созданных узлов
- `errors` - список ошибок

## Связанные компоненты

- `LibraryCoordinateParser` - извлечение координат
- `BytecodeParser` - парсинг структуры классов
- `HttpBytecodeAnalyzer` - анализ интеграционных вызовов
- `LibraryNodeIndex` - индекс для быстрого поиска
- `IntegrationPointService` - извлечение интеграционных точек
- `GraphLinker` - использование при линковке графа

