import logging
from contextlib import asynccontextmanager
from functools import lru_cache
from fastapi import FastAPI, Depends
import uvicorn

from app.core.config import get_settings
from app.schemas.evaluation import EvaluateRequest, EvaluateResponse
from app.services.orchestrator import EvaluationOrchestrator
from app.services.local_metrics import LocalMetricsService

logger = logging.getLogger(__name__)

# --- LIFESPAN ---
@asynccontextmanager
async def lifespan(app: FastAPI):
    # 1. Загружаем ML модели в память (CPU/GPU)
    logger.info("Startup: Warming up ML models...")
    try:
        LocalMetricsService.get_instance()
        logger.info("Startup: ML models loaded successfully")
    except Exception as e:
        logger.error("Startup: Failed to load ML models: %s", e)
        raise

    yield

    # 2. Закрываем ресурсы
    logger.info("Shutdown: Cleaning up resources...")

# --- APP SETUP ---
app = FastAPI(
    title=get_settings().PROJECT_NAME,
    version="1.0.0",
    lifespan=lifespan
)

# --- DEPENDENCY INJECTION ---
# NOTE: lru_cache is acceptable here — Settings are immutable after startup,
# and FastAPI runs judges in a single process so concurrent access is safe.
@lru_cache
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
        orchestrator: EvaluationOrchestrator = Depends(get_orchestrator)
):
    """
    Оценивает качество документации по коду.
    Использует: CodeBERT (локально) + GigaChat/Gemini/Ollama (LLM).
    """
    return await orchestrator.evaluate(request)

# --- ENTRY POINT ---
if __name__ == "__main__":
    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.APP_HOST,
        port=settings.APP_PORT,
        reload=settings.DEBUG,
    )
