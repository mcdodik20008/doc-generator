# SSE Streaming & Real-time Step Display

## Обзор

Doc-Generator теперь поддерживает **real-time отображение шагов обработки RAG запроса** через Server-Sent Events (SSE). Пользователь видит:

1. **Процесс обработки** — каждый шаг FSM по мере его выполнения
2. **Статус шагов** — в процессе (spinner), завершен (✓), ошибка (✗)
3. **Стриминг ответа** — ответ от LLM генерируется токен за токеном

## Архитектура

```
┌──────────────┐
│   Frontend   │
│  (chat.html) │
└──────┬───────┘
       │ POST /api/rag/ask/stream
       │
       ↓
┌──────────────────┐        ┌───────────────────────┐
│  RagController   │───────→│     RagService        │
│  (SSE handler)   │        │ prepareWithProgress() │
└──────────────────┘        └───────────┬───────────┘
       ↑                                 │
       │                                 ↓
       │                        ┌──────────────────────┐
       │                        │ GraphRequestProcessor│
       │                        │  (FSM execution)     │
       │                        └───────────┬──────────┘
       │                                    │
       │ SSE Events:                        │ Callback on each step
       │ - step (real-time)                 │
       │ - sources                          ↓
       │ - metadata                StepProgressCallback
       │ - token (streaming)       │
       │ - done                    ↓
       │                      StepEvent { type, status, description }
       │                           │
       └───────────────────────────┘
```

## SSE События

### 1. `step` (новое!)

Отправляется **в real-time** для каждого шага FSM.

```json
{
  "type": "EXACT_SEARCH",
  "status": "STARTED",
  "description": "Поиск точных совпадений...",
  "metadata": {},
  "timestamp": 1710412345678
}
```

**Статусы**:
- `STARTED` — шаг начал выполнение
- `IN_PROGRESS` — промежуточное обновление (если есть)
- `COMPLETED` — шаг успешно завершен (с `durationMs` в metadata)
- `FAILED` — шаг завершен с ошибкой
- `SKIPPED` — шаг пропущен

**Типы шагов** (`type`):
- `NORMALIZATION` — Нормализация запроса
- `EXTRACTION` — Извлечение классов/методов
- `EXACT_SEARCH` — Точный поиск по БД
- `REWRITING` — Переформулирование запроса
- `EXPANSION` — Расширение синонимами
- `VECTOR_SEARCH` — Векторный поиск
- `GRAPH_EXPANSION` — Построение графа зависимостей
- `RERANKING` — Ранжирование результатов

### 2. `sources`

Список найденных источников (нод из графа).

```json
[
  {
    "id": "123",
    "content": "class VariableUtils { ... }",
    "metadata": { "fqn": "com.example.VariableUtils", "kind": "CLASS" },
    "similarity": 0.95
  }
]
```

### 3. `metadata`

Метаданные обработки (приходит ПОСЛЕ всех step событий).

```json
{
  "originalQuery": "Как работает метод getPriority?",
  "rewrittenQuery": "Объясни getPriority в VariableUtils",
  "processingSteps": [ ... ]
}
```

### 4. `token`

Токен ответа от LLM (стриминг).

```
Метод getPriority в классе VariableUtils...
```

### 5. `done`

Завершение генерации ответа.

```
""
```

### 6. `error`

Ошибка при обработке.

```json
{
  "message": "Превышено время ожидания"
}
```

## Frontend (chat.html)

### CSS

```css
.live-step {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 5px 10px;
  background: var(--surface2);
  border-radius: 4px;
  animation: fadeSlideIn 0.3s ease forwards;
}

.live-step.active {
  background: rgba(99, 102, 241, 0.1); /* Highlighted when in progress */
}

.live-step .spinner {
  width: 12px;
  height: 12px;
  border: 2px solid var(--accent);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.live-step .step-icon.completed { color: var(--green); }
.live-step .step-icon.failed { color: var(--red); }
.live-step .step-icon.started { color: var(--accent); }
```

### JavaScript

**Обработка событий**:

```javascript
case 'step':
  try {
    const stepData = JSON.parse(evt.data);
    updateLiveStep(stepData);
  } catch (e) {
    console.error('Failed to parse step data:', e);
  }
  break;
```

**Функция обновления шага**:

```javascript
function updateLiveStep(stepData) {
  const stepType = stepData.type;
  const stepStatus = stepData.status?.toLowerCase();
  const stepId = `step-${stepType}`;

  let stepEl = document.getElementById(stepId);

  if (!stepEl) {
    stepEl = document.createElement('div');
    stepEl.className = 'live-step';
    stepEl.id = stepId;
    stepsContainer.appendChild(stepEl);
  }

  let icon;
  if (stepStatus === 'started' || stepStatus === 'in_progress') {
    icon = '<div class="spinner"></div>';
    stepEl.classList.add('active');
  } else if (stepStatus === 'completed') {
    icon = '✓';
    stepEl.classList.remove('active');
  } else if (stepStatus === 'failed') {
    icon = '✗';
    stepEl.classList.remove('active');
  }

  stepEl.innerHTML = `
    <span class="step-icon">${icon}</span>
    <span class="step-name">${stepName}</span>
    ${duration ? `<span class="step-duration">${duration}ms</span>` : ''}
  `;
}
```

## Backend (Kotlin)

### StepEvent DTO

```kotlin
data class StepEvent(
    val stepType: ProcessingStepType,
    val status: StepEventStatus,
    val description: String,
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
)

enum class StepEventStatus {
    STARTED, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
}
```

### GraphRequestProcessor

Отправляет callback при каждом шаге:

```kotlin
fun process(
    query: String,
    sessionId: String,
    applicationId: Long?,
    stepCallback: StepProgressCallback? = null
): QueryProcessingContext {
    // ...

    // Начало шага
    stepCallback?.onStepUpdate(
        StepEvent(
            stepType = currentStep,
            status = StepEventStatus.STARTED,
            description = getStepDescription(currentStep, STARTED)
        )
    )

    val result = step.execute(context)

    // Завершение шага
    stepCallback?.onStepUpdate(
        StepEvent(
            stepType = currentStep,
            status = StepEventStatus.COMPLETED,
            description = getStepDescription(currentStep, COMPLETED),
            metadata = mapOf("durationMs" to duration)
        )
    )
}
```

### RagController

Использует Reactor Sinks для стриминга:

```kotlin
@PostMapping("/ask/stream")
fun askStream(@RequestBody request: RagRequest): Flux<ServerSentEvent<String>> {
    val stepSink = Sinks.many().multicast().onBackpressureBuffer<StepEvent>()

    val stepCallback = StepProgressCallback { event ->
        stepSink.tryEmitNext(event)
    }

    val preparedMono = Mono.fromCallable {
        ragService.prepareContextWithProgress(
            request.query,
            request.sessionId,
            request.applicationId,
            stepCallback
        )
    }.doFinally { stepSink.tryEmitComplete() }

    val stepEvents = stepSink.asFlux().map { stepEvent ->
        ServerSentEvent.builder<String>()
            .event("step")
            .data(objectMapper.writeValueAsString(stepEvent))
            .build()
    }

    return stepEvents.concatWith(...)
}
```

## Преимущества

### Для пользователя

✅ **Видимость процесса** — понятно, что происходит (не просто spinner)
✅ **Быстрая обратная связь** — видно результат каждого шага сразу
✅ **Диагностика** — если шаг завис, видно на каком именно
✅ **Стриминг ответа** — начинает читать ответ до завершения генерации

### Для разработчика

✅ **Отладка** — легко понять, где проблема в pipeline
✅ **Мониторинг** — видно время каждого шага
✅ **UX** — пользователь не думает, что приложение зависло

## Примеры использования

### Успешный запрос

```
[Spinner] Нормализация запроса...
[✓] Нормализация запроса (15ms)
[Spinner] Извлечение сущностей...
[✓] Извлечение сущностей (120ms)
[Spinner] Точный поиск...
[✓] Точный поиск (45ms)
[Spinner] Построение графа...
[✓] Построение графа (230ms)

[Стриминг ответа начинается...]
Метод getPriority в классе VariableUtils используется для...
```

### Запрос с ошибкой

```
[✓] Нормализация запроса (10ms)
[✓] Извлечение сущностей (95ms)
[✓] Точный поиск (30ms)
[Spinner] Переформулирование...
[✗] Переформулирование (Превышено время ожидания)

[Fallback ответ]
Не удалось обработать запрос — превышено время ожидания.
```

## Дальнейшие улучшения

### Кэширование шагов

Кэшировать результаты `ExactSearchStep` и `VectorSearchStep`:

```kotlin
@Cacheable("exactSearch", key = "#className + ':' + #methodName")
fun findNodes(className: String?, methodName: String?): List<Node>
```

### Прогресс-бар

Добавить индикатор прогресса:

```javascript
const totalSteps = 8;
const completedSteps = 3;
const progress = (completedSteps / totalSteps) * 100; // 37.5%
```

### Статистика производительности

Собирать метрики времени выполнения шагов:

```yaml
# Prometheus
rag_step_duration_seconds{step="exact_search"} 0.045
rag_step_duration_seconds{step="vector_search"} 0.230
```

### Retry логика

Автоматический retry для failed шагов:

```kotlin
if (stepStatus == FAILED && retryCount < MAX_RETRIES) {
    stepCallback.onStepUpdate(StepEvent(..., status = IN_PROGRESS))
    // Retry...
}
```

## Troubleshooting

### Шаги не отображаются

1. Проверьте, что `stepCallback` передается в `GraphRequestProcessor`:
   ```kotlin
   graphRequestProcessor.process(query, sessionId, appId, stepCallback)
   ```

2. Проверьте логи:
   ```
   LLM request started: model=qwen2.5-coder:14b
   ```

3. Откройте DevTools → Network → смотрите SSE поток

### Шаги зависают на STARTED

Скорее всего таймаут слишком короткий. Увеличьте:

```yaml
docgen:
  rag:
    step-timeout-seconds: 90
```

### Слишком медленно

1. Уменьшите размер контекста (см. RAG_OPTIMIZATION.md)
2. Используйте более легкую модель для REWRITING
3. Добавьте кэширование
