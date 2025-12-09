import pytest
from app.services.orchestrator import EvaluationOrchestrator
from app.schemas.evaluation import EvaluateRequest

@pytest.mark.asyncio
async def test_evaluate_flow(mock_local_metrics, mock_llm_judges):
    orchestrator = EvaluationOrchestrator()
    
    request = EvaluateRequest(
        code_snippet="def foo(): pass",
        generated_doc="Documentation for foo"
    )
    
    response = await orchestrator.evaluate(request)
    
    assert response.final_score > 0
    assert response.llm_scores.gigachat == 8.5
    assert response.llm_scores.gemini == 9.0
    assert response.llm_scores.ollama == 8.0
    
    assert response.score_variance >= 0
    assert 0 <= response.confidence_score <= 1
    
    # Проверяем вызовы
    mock_local_metrics.calculate_semantic_similarity.assert_called_once()
    mock_local_metrics.calculate_coverage.assert_called_once()
    mock_local_metrics.calculate_readability.assert_called_once()
    
    for judge in mock_llm_judges.values():
        judge.assert_called_once()

@pytest.mark.asyncio
async def test_evaluate_fallback(mock_local_metrics, mock_llm_judges):
    # Настраиваем LLM на возврат None (ошибка)
    for judge in mock_llm_judges.values():
        judge.return_value = None
        
    orchestrator = EvaluationOrchestrator()
    request = EvaluateRequest(code_snippet="def code(): pass", generated_doc="Documentation for code")
    
    response = await orchestrator.evaluate(request)
    
    # Должен быть фоллбек на локальные метрики
    # (sem + cov) / 2 = (8.0 + 9.0) / 2 = 8.5
    assert response.final_score == 8.5
    assert response.llm_scores.gigachat is None
