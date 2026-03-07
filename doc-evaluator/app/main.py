import logging
from contextlib import asynccontextmanager
from functools import lru_cache
from fastapi import FastAPI, Depends, Request
from fastapi.responses import JSONResponse
from slowapi import Limiter
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
import uvicorn

from app.core.config import get_settings
from app.schemas.evaluation import EvaluateRequest, EvaluateResponse
from app.services.orchestrator import EvaluationOrchestrator
from app.services.local_metrics import LocalMetricsService

logger = logging.getLogger(__name__)

settings = get_settings()

limiter = Limiter(key_func=get_remote_address)


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

    # 2. Закрываем ресурсы судей (aiohttp sessions и т.д.)
    logger.info("Shutdown: Cleaning up resources...")
    try:
        orchestrator = get_orchestrator()
        for _, judge in orchestrator.judges:
            if hasattr(judge, 'close'):
                await judge.close()
        logger.info("Shutdown: All judge resources released")
    except Exception as e:
        logger.warning("Shutdown: Error during cleanup: %s", e)


# --- APP SETUP ---
app = FastAPI(
    title=settings.PROJECT_NAME,
    version="1.0.0",
    lifespan=lifespan
)
app.state.limiter = limiter


@app.exception_handler(RateLimitExceeded)
async def rate_limit_handler(request: Request, exc: RateLimitExceeded):
    return JSONResponse(
        status_code=429,
        content={"detail": "Rate limit exceeded. Please try again later."}
    )


# --- DEPENDENCY INJECTION ---
@lru_cache
def get_orchestrator() -> EvaluationOrchestrator:
    return EvaluationOrchestrator()


# --- ENDPOINTS ---
@app.get("/health", tags=["System"])
async def health_check():
    """Проверка доступности сервиса (для Kubernetes/Docker)"""
    return {"status": "ok", "service": "Doc Evaluator"}


@app.post("/evaluate", response_model=EvaluateResponse, tags=["Evaluation"])
@limiter.limit(f"{settings.RATE_LIMIT_PER_MINUTE}/minute")
async def evaluate_endpoint(
        request: Request,
        body: EvaluateRequest,
        orchestrator: EvaluationOrchestrator = Depends(get_orchestrator)
):
    """
    Оценивает качество документации по коду.
    Использует: CodeBERT (локально) + GigaChat/Gemini/Ollama/Qwen (LLM).
    """
    return await orchestrator.evaluate(body)


# --- ENTRY POINT ---
if __name__ == "__main__":
    uvicorn.run(
        "app.main:app",
        host=settings.APP_HOST,
        port=settings.APP_PORT,
        reload=settings.DEBUG,
    )
