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
    assert response.llm_scores.qwen == 7.5

    assert response.score_variance >= 0
    assert 0 <= response.confidence_score <= 1

    # Проверяем что все локальные метрики вызваны
    mock_local_metrics.calculate_semantic_similarity.assert_called_once()
    mock_local_metrics.calculate_coverage.assert_called_once()
    mock_local_metrics.calculate_readability.assert_called_once()

    # Проверяем что все судьи вызваны
    for judge in mock_llm_judges.values():
        judge.assert_called_once()


@pytest.mark.asyncio
async def test_evaluate_fallback(mock_local_metrics, mock_llm_judges):
    """Все LLM возвращают None — fallback на локальные метрики"""
    for judge in mock_llm_judges.values():
        judge.return_value = None

    orchestrator = EvaluationOrchestrator()
    request = EvaluateRequest(
        code_snippet="def code(): pass",
        generated_doc="Documentation for code"
    )

    response = await orchestrator.evaluate(request)

    # Fallback: (sem + cov + readability) / 3 = (0.8 + 9.0 + 7.5) / 3 ≈ 5.77
    expected = round((0.8 + 9.0 + 7.5) / 3, 2)
    assert response.final_score == expected
    assert response.confidence_score == 0.0
    assert response.llm_scores.gigachat is None
    assert response.llm_scores.gemini is None
    assert response.llm_scores.ollama is None
    assert response.llm_scores.qwen is None


@pytest.mark.asyncio
async def test_evaluate_weighted_formula(mock_local_metrics, mock_llm_judges):
    """Проверяем корректность взвешенной формулы с readability"""
    mock_local_metrics.calculate_semantic_similarity.return_value = 10.0
    mock_local_metrics.calculate_coverage.return_value = 10.0
    mock_local_metrics.calculate_readability.return_value = 10.0

    for judge in mock_llm_judges.values():
        judge.return_value = 10.0

    orchestrator = EvaluationOrchestrator()
    request = EvaluateRequest(
        code_snippet="fun perfect(): Unit",
        generated_doc="Perfect documentation"
    )

    response = await orchestrator.evaluate(request)

    # 10*0.15 + 10*0.15 + 10*0.1 + 10*0.6 = 10.0
    assert response.final_score == 10.0
