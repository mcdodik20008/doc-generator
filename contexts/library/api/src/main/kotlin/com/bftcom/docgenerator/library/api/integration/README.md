# Использование информации об интеграционных точках

После анализа библиотек информация об HTTP/Kafka/Camel вызовах сохраняется в `LibraryNode.meta.integrationAnalysis`.

## Структура данных

В метаданных каждого метода библиотеки сохраняется:

```json
{
  "integrationAnalysis": {
    "isParentClient": true,
    "urls": ["http://api.example.com/users", "http://api.example.com/posts"],
    "httpMethods": ["GET", "POST"],
    "hasRetry": true,
    "hasTimeout": true,
    "hasCircuitBreaker": false,
    "kafkaTopics": ["user-events", "notifications"],
    "kafkaCalls": [
      {
        "topic": "user-events",
        "operation": "PRODUCE",
        "clientType": "KafkaProducer"
      }
    ],
    "camelUris": ["kafka:user-events", "http://api.example.com"],
    "camelCalls": [
      {
        "uri": "kafka:user-events",
        "endpointType": "kafka",
        "direction": "FROM"
      }
    ]
  }
}
```

## API для работы с интеграционными точками

### 1. Поиск методов по URL

```http
GET /api/integration/methods/by-url?url=http://api.example.com/users&libraryId=1
```

Находит все методы библиотеки, которые вызывают указанный URL.

### 2. Поиск методов по Kafka topic

```http
GET /api/integration/methods/by-kafka-topic?topic=user-events&libraryId=1
```

Находит все методы, которые используют указанный Kafka topic.

### 3. Поиск методов по Camel URI

```http
GET /api/integration/methods/by-camel-uri?uri=kafka:user-events&libraryId=1
```

Находит все методы, которые используют указанный Camel URI.

### 4. Получение сводки по методу

```http
GET /api/integration/method/summary?methodFqn=com.example.Client.getUser&libraryId=1
```

Возвращает полную сводку по всем интеграционным точкам метода.

### 5. Поиск родительских клиентов

```http
GET /api/integration/parent-clients?libraryId=1
```

Находит все методы библиотеки, которые являются родительскими клиентами (верхнеуровневые API-методы).

## Использование в коде

### IntegrationPointService

```kotlin
@Autowired
private lateinit var integrationPointService: IntegrationPointService

// Найти все методы, которые вызывают URL
val methods = integrationPointService.findMethodsByUrl("http://api.example.com/users")

// Получить сводку по методу
val summary = integrationPointService.getMethodIntegrationSummary(
    methodFqn = "com.example.Client.getUser",
    libraryId = 1L
)

// Найти родительские клиенты
val parentClients = integrationPointService.findParentClients(libraryId = 1L)
```

### IntegrationPointLinker

```kotlin
@Autowired
private lateinit var integrationPointLinker: IntegrationPointLinker

// Создать связи между методами приложения и интеграционными точками
val result = integrationPointLinker.linkIntegrationPoints(application)
```

## Следующие шаги

1. **Построение графа зависимостей**: Использовать `IntegrationPointLinker` для создания Edge между методами приложения и интеграционными точками.

2. **Визуализация**: Отображать интеграционные точки на графе приложения.

3. **Анализ зависимостей**: Находить все сервисы, которые используют определенный URL или Kafka topic.

4. **Документирование**: Автоматически генерировать документацию по API-клиентам на основе информации о родительских клиентах.

5. **Мониторинг изменений**: Отслеживать изменения в интеграционных точках при обновлении библиотек.

