from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.core.config import get_settings
from app.schemas.evaluation import EvaluateRequest, EvaluateResponse
from app.services.orchestrator import EvaluationOrchestrator
from app.services.local_metrics import LocalMetricsService

@asynccontextmanager
async def lifespan(app: FastAPI):
    LocalMetricsService.get_instance()
    yield

app = FastAPI(
    title=get_settings().PROJECT_NAME,
    lifespan=lifespan
)

def get_orchestrator():
    return EvaluationOrchestrator()

@app.post("/evaluate", response_model=EvaluateResponse)
async def evaluate_endpoint(request: EvaluateRequest):
    orchestrator = get_orchestrator()
    return await orchestrator.evaluate(request)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)