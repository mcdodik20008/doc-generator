import pytest
from unittest.mock import AsyncMock, MagicMock
from fastapi.testclient import TestClient
from app.main import app
from app.services.local_metrics import LocalMetricsService
from app.services.llm_judges import GigaChatJudge, GeminiJudge, OllamaJudge, QwenJudge

@pytest.fixture
def client():
    return TestClient(app)

@pytest.fixture
def mock_local_metrics(monkeypatch):
    mock_service = MagicMock(spec=LocalMetricsService)
    mock_service.calculate_semantic_similarity.return_value = 0.8
    mock_service.calculate_coverage.return_value = 9.0
    mock_service.calculate_readability.return_value = 7.5

    monkeypatch.setattr(LocalMetricsService, "get_instance", lambda: mock_service)
    return mock_service

@pytest.fixture
def mock_llm_judges(monkeypatch):
    mock_giga = AsyncMock(return_value=8.5)
    mock_gemini = AsyncMock(return_value=9.0)
    mock_ollama = AsyncMock(return_value=8.0)
    mock_qwen = AsyncMock(return_value=7.5)

    monkeypatch.setattr(GigaChatJudge, "evaluate", mock_giga)
    monkeypatch.setattr(GeminiJudge, "evaluate", mock_gemini)
    monkeypatch.setattr(OllamaJudge, "evaluate", mock_ollama)
    monkeypatch.setattr(QwenJudge, "evaluate", mock_qwen)

    return {
        "gigachat": mock_giga,
        "gemini": mock_gemini,
        "ollama": mock_ollama,
        "qwen": mock_qwen,
    }
