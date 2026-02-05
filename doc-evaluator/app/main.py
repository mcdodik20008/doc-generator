from contextlib import asynccontextmanager
from functools import lru_cache # Добавил кэширование
from fastapi import FastAPI, Depends # Добавил Depends
import uvicorn

from app.core.config import get_settings
from app.schemas.evaluation import EvaluateRequest, EvaluateResponse
from app.services.orchestrator import EvaluationOrchestrator
from app.services.local_metrics import LocalMetricsService

# --- LIFESPAN ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 1. Загружаем ML модели в память (CPU/GPU)
    # TODO: Использовать logging вместо print
    # TODO: Добавить обработку ошибок при загрузке моделей (может упасть при старте)
    # TODO: Добавить health check для проверки готовности моделей
    print("Startup: Warming up ML models...")
    LocalMetricsService.get_instance()

    yield

    # 2. Тут можно закрыть соединения (например, aiohttp sessions), если бы они хранились глобально
    # TODO: Добавить graceful shutdown для ML моделей (освобождение памяти)
    # TODO: Закрывать aiohttp сессии судей если они используются
    print("Shutdown: Cleaning up resources...")

# --- APP SETUP ---
app = FastAPI(
    title=get_settings().PROJECT_NAME,
    version="1.0.0",
    lifespan=lifespan
)

# --- DEPENDENCY INJECTION ---
# TODO: Проблема с lru_cache - если settings изменятся, оркестратор не обновится
# TODO: Рассмотреть использование зависимостей FastAPI вместо глобального кеша
# TODO: В многопоточной среде может быть проблема с concurrent доступом к судьям
@lru_cache # Кэшируем оркестратор, чтобы не создавать судей (GigaChatJudge и т.д.) на каждый запрос заново
def get_orchestrator() -> EvaluationOrchestrator:
    return EvaluationOrchestrator()

# --- ENDPOINTS ---
@app.get("/health", tags=["System"])
async def health_check():
    """Проверка доступности сервиса (для Kubernetes/Docker)"""
    return {"status": "ok", "service": "Doc Evaluator"}

@app.post("/evaluate", response_model=EvaluateResponse, tags=["Evaluation"])
async def evaluate_endpoint(
        request: EvaluateRequest,
        # FastAPI сам внедрит зависимость. Если захочешь протестировать,
        # сможешь легко подменить get_orchestrator на mock.
        orchestrator: EvaluationOrchestrator = Depends(get_orchestrator)
):
    """
    Оценивает качество документации по коду.
    Использует: CodeBERT (локально) + GigaChat/Gemini/Ollama (LLM).
    """
    return await orchestrator.evaluate(request)

# --- ENTRY POINT ---
if __name__ == "__main__":
    # Указываем reload=True только для локальной разработки
    # В проде запускают через: uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 4
    # TODO: reload=True не должен быть hardcoded - использовать переменную окружения (например, DEBUG)
    # TODO: Порт 8000 hardcoded - вынести в конфигурацию
    # TODO: host="0.0.0.0" может быть небезопасно для production - использовать конфигурацию
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)