# Doc Evaluator — Рекомендации по улучшению

## Архитектура

```
Kotlin App (Spring Boot)                    Python Microservice (FastAPI)
┌─────────────────────┐                    ┌──────────────────────────┐
│ RagController       │   HTTP POST        │ /evaluate                │
│  /ask-with-val      │ ──────────────►    │                          │
│                     │                    │ EvaluationOrchestrator   │
│ DocEvaluatorClient  │ ◄──────────────    │  ├─ LocalMetricsService  │
│  (WebClient)        │   JSON response    │  │   ├─ CodeBERT         │
│                     │                    │  │   ├─ Keyword Coverage  │
└─────────────────────┘                    │  │   └─ Readability       │
                                           │  └─ LLM Judges           │
                                           │      ├─ GigaChat         │
                                           │      ├─ Gemini           │
                                           │      ├─ Ollama           │
                                           │      └─ Qwen             │
                                           └──────────────────────────┘
```

Общая оценка архитектуры: **7/10 -> 9/10** после применения исправлений.

---

## Что сделано хорошо

1. **Разделение на микросервисы** — Python-сервис отдельно от Kotlin-приложения. ML-модели (CodeBERT, torch) логичнее держать в Python.
2. **Multi-judge подход** — несколько LLM-судей (GigaChat, Gemini, Ollama, Qwen) с агрегацией дают более объективную оценку, чем один судья.
3. **Self-Consistency Rounds** — запуск судей с разными температурами и усреднение — хорошая практика для повышения надёжности.
4. **Graceful degradation** — если LLM недоступны, система откатывается на локальные метрики. Если doc-evaluator недоступен, RAG всё равно работает.
5. **Валидация конфигурации** — проверка суммы весов = 1.0, валидация URL, лимиты на rounds.
6. **Thread-safe Singleton** для LocalMetricsService — double-checked locking для тяжёлой модели CodeBERT.

---

## Сводная таблица

| # | Что | Сложность | Импакт | Статус |
|---|-----|-----------|--------|--------|
| 1 | Подключить QwenJudge в оркестратор | Лёгкая | Высокий | DONE |
| 2 | Включить readability в формулу final_score | Лёгкая | Высокий | DONE |
| 3 | Закрывать `aiohttp.ClientSession` в lifespan | Лёгкая | Высокий | DONE |
| 4 | Перенести `genai.configure()` в `__init__` | Лёгкая | Высокий | DONE |
| 5 | Переписать `DocEvaluatorClient` на нативный Mono | Средняя | Высокий | DONE |
| 6 | Улучшить JUDGE_PROMPT с рубрикой оценки | Средняя | Высокий | DONE |
| 7 | Добавить QwenJudge в LlmScores + Kotlin DTO | Средняя | Средний | DONE |
| 8 | Добавить rate limiting в FastAPI (slowapi) | Средняя | Средний | DONE |
| 9 | Написать edge-case и integration тесты | Средняя | Средний | DONE |
| 10 | Вынести модель Gemini в конфиг (`GEMINI_MODEL`) | Лёгкая | Низкий | DONE |
| 11 | Добавить retry-логику для LLM-судей | Средняя | Средний | DONE |
| 12 | Исправить f-strings на %s в логгировании | Лёгкая | Низкий | DONE |
| 13 | Убрать module-level settings в пользу DI | Лёгкая | Низкий | DONE |
| 14 | Рассмотреть замену CodeBERT на модель для code-doc similarity | Сложная | Высокий | TODO |
| 15 | Добавить поддержку русского в readability (razdel/natasha) | Средняя | Средний | TODO |

---

## Что было исправлено

### Критические (1-4)

**1. QwenJudge подключён в оркестратор**
- Добавлен `("qwen", QwenJudge())` в `orchestrator.py`
- Добавлено поле `qwen` в `LlmScores` (Python + Kotlin)

**2. Readability включён в формулу**
- Новые веса: `WEIGHT_SEMANTIC=0.15`, `WEIGHT_COVERAGE=0.15`, `WEIGHT_READABILITY=0.1`, `WEIGHT_LLM=0.6`
- `WEIGHT_READABILITY` добавлен в `config.py` с валидацией
- Формула обновлена в `orchestrator.py`
- Fallback обновлён: `(sem + cov + readability) / 3`

**3. Утечка ресурсов исправлена**
- `main.py` lifespan теперь закрывает все judge-ресурсы при shutdown
- Вызывается `judge.close()` для всех судей, имеющих этот метод

**4. GeminiJudge исправлен**
- `genai.configure()` перенесён в `__init__()`
- Модель вынесена в конфиг: `GEMINI_MODEL` в `config.py`

### Серьёзные (5-8)

**5. DocEvaluatorClient переписан на Mono**
- Новый метод `evaluateAsync()` возвращает `Mono<EvaluationResult>`
- Новый метод `healthCheckAsync()` возвращает `Mono<Boolean>`
- Старые блокирующие методы сохранены для обратной совместимости
- `RagController.askWithValidation()` теперь возвращает `Mono<ValidatedRagResponse>` — нативно реактивный
- Убрана двойная обёртка `CompletableFuture` + `.block()`

**6. JUDGE_PROMPT улучшен**
- Добавлена роль: "Senior Technical Writer с 10-летним опытом"
- 4 критерия оценки: Полнота, Точность, Ясность, Структура
- Подробная шкала: 0-2, 3-4, 5-6, 7-8, 9-10
- Поддержка дробных оценок (7.5)

**7. LlmScores расширена**
- Добавлено поле `qwen` в Python (`evaluation.py`) и Kotlin (`LlmScores.kt`)
- `EvaluationResult.kt` работает без изменений через Jackson

**8. Rate limiting добавлен**
- `slowapi` интегрирован в FastAPI
- `RATE_LIMIT_PER_MINUTE=30` в конфигурации
- Custom 429 error handler

### Средние (9-13)

**9. Тесты написаны**
- `test_api_edge_cases.py`: 12 edge-case тестов (пустые поля, Unicode, большой код, спецсимволы, LLM fallback, невалидный Content-Type)
- `test_integration.py`: 5 интеграционных тестов (полный pipeline, partial failure, all fail, exceptions, weights)
- `test_orchestrator.py` обновлён: учитывает qwen и новые веса
- `test_api.py` обновлён: проверяет qwen в ответе
- `conftest.py` обновлён: mock для QwenJudge
- `test_llm_judges_real.py`: добавлен тест для Qwen

**11. Retry-логика добавлена**
- `BaseJudge._retry_evaluate()` — обёртка с exponential backoff
- `MAX_RETRIES=2`, `RETRY_DELAY_SECONDS=1.0`
- Перехватывает `TimeoutError`, `ConnectionError`, `OSError`
- Все судьи используют retry

**12-13. Code quality**
- f-strings заменены на `%s`-placeholder'ы в `local_metrics.py`
- Module-level `settings = get_settings()` убран из `orchestrator.py` и `llm_judges.py`
- Settings теперь хранятся в `self._settings` через DI в `__init__()`

---

## Оставшиеся TODO

### 14. Замена CodeBERT на модель для code-doc similarity

**Проблема:** CodeBERT обучен на code understanding. Cosine similarity между кодом и NL-документацией — cross-domain, результаты ненадёжны.

**Кандидаты:**
- `microsoft/unixcoder-base` — обучен на code-NL пары
- `Salesforce/codet5-base` — encoder-decoder для code understanding
- Fine-tuned sentence-transformers на CodeSearchNet

**Сложность:** Средняя-Высокая (нужно бенчмаркить, менять размер модели, возможно менять Dockerfile).

### 15. Поддержка русского языка в readability

**Проблема:** Flesch Reading Ease рассчитан на английский. Для русского текста метрика бессмысленна.

**Решение:**
1. Определять язык документации (langdetect / lingua)
2. Для русского: использовать `razdel` (токенизация) + формулу Лихачёва или Coleman-Liau адаптированную
3. Для английского: оставить textstat

**Зависимости:** `langdetect` или `lingua-language-detector`, `razdel`
