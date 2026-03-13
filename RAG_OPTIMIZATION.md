# Оптимизация RAG для удаленной LLM (Mac mini M4)

## Проблемы и решения

### Проблема 1: Таймауты при работе через сеть

**Симптомы**:
```
TimeoutException на шаге REWRITING
LLM request в progress более 30 секунд
```

**Причина**: Ollama развернута на отдельной машине (Mac mini M4), сетевая латентность + время обработки.

**Решение**: Увеличены таймауты в `application.yml`:

```yaml
docgen:
  rag:
    processing-timeout-seconds: 120  # было 45
    llm-timeout-seconds: 90          # было 30
    step-timeout-seconds: 90         # было 30
```

### Проблема 2: Слишком большой контекст для LLM

**Симптомы**:
```
LLM request started: chars=162890 (при лимите 30000!)
Медленная обработка запросов
```

**Причина**: Система отправляет слишком много данных в контекст LLM.

**Решение**: Уменьшены лимиты контекста:

```yaml
docgen:
  rag:
    max-context-chars: 12000         # было 30000
    max-node-code-chars: 1500        # было 3000
    max-graph-relations-chars: 2000  # было 5000
    max-exact-nodes: 3               # было 5
    max-neighbor-nodes: 5            # было 10
```

### Проблема 3: ExactSearchStep не находит классы

**Симптомы**:
```
EXACT_SEARCH: class='variableUtils', method='getPriority', nodes=0
```

**Причина**: Поиск чувствителен к регистру (`variableUtils` vs `VariableUtils`).

**Решение**: Добавлены case-insensitive методы поиска:
- `findByApplicationIdAndClassNameIgnoreCase()`
- `findByApplicationIdAndClassNameAndMethodNameIgnoreCase()`
- `findByApplicationIdAndMethodNameIgnoreCase()`

Используют `LOWER()` в SQL запросах:
```sql
WHERE LOWER(n.name) = LOWER(:className)
```

## Настройка через переменные окружения

Все параметры можно переопределить:

```bash
# Таймауты
export RAG_PROCESSING_TIMEOUT=120
export RAG_LLM_TIMEOUT=90
export RAG_STEP_TIMEOUT=90

# Размеры контекста
export RAG_MAX_CONTEXT_CHARS=12000
export RAG_MAX_NODE_CODE_CHARS=1500
export RAG_MAX_RELATIONS_CHARS=2000

# Лимиты узлов
export RAG_MAX_EXACT_NODES=3
export RAG_MAX_NEIGHBOR_NODES=5
```

## Рекомендации по дальнейшей оптимизации

### 1. Кэширование результатов

Добавить Redis кэш для:
- Результатов ExactSearchStep (TTL: 5-10 минут)
- Результатов VectorSearchStep (TTL: 2-5 минут)
- Переформулированных запросов (TTL: 1 час)

```kotlin
@Cacheable("exactSearch", key = "#className + ':' + #methodName")
fun findNodes(className: String?, methodName: String?): List<Node>
```

### 2. Оптимизация модели для переформулирования

Сейчас используется `qwen2.5-coder:14b` для REWRITING шага — слишком тяжелая модель для простой задачи.

**Решение**: Использовать более легкую модель:
```yaml
spring:
  ai:
    clients:
      rewriter:  # Новый клиент только для переформулирования
        model: qwen2.5:3b  # Легче, чем 14b
        temperature: 0.5
```

### 3. Батчинг запросов к LLM

Для генерации документации группировать узлы в батчи:

```kotlin
// Вместо:
nodes.forEach { node ->
    llm.generate(node)  // N запросов
}

// Делать:
nodes.chunked(10).forEach { batch ->
    llm.generateBatch(batch)  // N/10 запросов
}
```

### 4. Streaming ответов

Уже реализовано в `/api/rag/ask/stream`, но можно оптимизировать:
- Отправлять частичные результаты по мере готовности
- Не ждать полной обработки всех шагов FSM

### 5. Предварительный прогрев (warm-up)

При старте приложения:
```kotlin
@PostConstruct
fun warmupLLM() {
    // Отправить простой запрос для прогрева модели
    llmClient.generate("Hello")
}
```

### 6. Мониторинг производительности

Добавить метрики:
```kotlin
@Timed("rag.step.exact_search")
@Counted("rag.step.exact_search.invocations")
fun execute(context: QueryProcessingContext): StepResult
```

Prometheus метрики:
- `rag_step_duration_seconds{step="exact_search"}`
- `rag_llm_request_duration_seconds{model="qwen2.5-coder:14b"}`
- `rag_context_size_chars{step="rewriting"}`

### 7. Адаптивные таймауты

Динамически подстраивать таймауты под текущую нагрузку:

```kotlin
val baseTimeout = 30
val currentLoad = systemMetrics.getLoad()
val adaptiveTimeout = baseTimeout * (1 + currentLoad)
```

### 8. Параллельная обработка шагов

Некоторые шаги FSM можно выполнять параллельно:

```kotlin
// ExactSearchStep и VectorSearchStep можно запускать параллельно
val exactResults = async { exactSearchStep.execute(context) }
val vectorResults = async { vectorSearchStep.execute(context) }

val combined = combine(exactResults.await(), vectorResults.await())
```

## Использование Mac mini M4 эффективно

### Модели для разных задач

**Для генерации документации** (медленно, качественно):
- `qwen2.5-coder:14b` — основная модель
- Альтернатива: `deepseek-coder:6.7b` (быстрее, чуть хуже качество)

**Для переформулирования** (быстро):
- `qwen2.5:3b` или `qwen2.5:0.5b`

**Для embeddings**:
- `bge-m3` (текущая) — хороший баланс
- Альтернатива: `nomic-embed-text` (быстрее)

### Настройка num_ctx

Для больших контекстов увеличить:
```yaml
spring:
  ai:
    ollama:
      embedding:
        options:
          num_ctx: 8192  # Текущее значение
      chat:
        options:
          num_ctx: 16384  # Для больших запросов
```

### Проверка доступности Ollama

```bash
# Проверить статус
curl http://192.168.0.15:11434/api/ps

# Проверить доступные модели
curl http://192.168.0.15:11434/api/tags
```

## Отладка

### Включить подробное логирование

```yaml
logging:
  level:
    com.bftcom.docgenerator.rag: DEBUG
    com.bftcom.docgenerator.ai: DEBUG
```

### Проверить метрики через Prometheus

```bash
curl http://localhost:8080/actuator/prometheus | grep rag
```

### Проверить время выполнения шагов

Логи содержат:
```
LLM response received: model=qwen2.5:0.5b, durationMs=1957, tokens[prompt=305, completion=15, total=320]
```

## Типичные значения для Mac mini M4 (32GB)

| Модель | Контекст | Время ответа (локально) | Время ответа (через сеть) |
|--------|----------|------------------------|---------------------------|
| qwen2.5:0.5b | 1K chars | ~500ms | ~1-2s |
| qwen2.5:3b | 1K chars | ~1s | ~2-3s |
| qwen2.5-coder:14b | 1K chars | ~3-5s | ~5-10s |
| qwen2.5-coder:14b | 10K chars | ~10-15s | ~15-30s |

**Рекомендация**: Держать контекст < 12K символов для быстрых ответов.

## Checklist для production

- [ ] Изменить пароль `admin/admin123!@#` на более сильный
- [ ] Включить HTTPS (nginx/Caddy reverse proxy)
- [ ] Настроить мониторинг (Prometheus + Grafana)
- [ ] Настроить алерты на длительные таймауты RAG
- [ ] Добавить rate limiting для `/api/rag/ask/stream`
- [ ] Настроить логротацию для `logs/doc-generator.log`
- [ ] Добавить health check для Ollama в `/actuator/health`
- [ ] Настроить резервное копирование PostgreSQL
- [ ] Проверить лимиты памяти JVM (`-Xmx`, `-Xms`)
- [ ] Настроить connection pool для БД
