"""
Интеграционные тесты для полного пайплайна оценки.
Тестируют взаимодействие компонентов без реальных LLM-вызовов.
"""
import pytest
from unittest.mock import AsyncMock
from app.services.orchestrator import EvaluationOrchestrator
from app.schemas.evaluation import EvaluateRequest


@pytest.mark.asyncio
async def test_full_pipeline_all_judges_respond(mock_local_metrics, mock_llm_judges):
    """Полный пайплайн: все локальные метрики + все LLM судьи возвращают оценки"""
    orchestrator = EvaluationOrchestrator()

    request = EvaluateRequest(
        code_snippet="fun calculateTotal(items: List<Item>): Double = items.sumOf { it.price }",
        generated_doc="Calculates total price of all items in the list by summing their prices"
    )

    response = await orchestrator.evaluate(request)

    # Все метрики должны быть вычислены
    assert response.semantic_score >= 0
    assert response.keyword_coverage >= 0
    assert response.readability_score >= 0

    # Все LLM судьи должны ответить
    assert response.llm_scores.gigachat is not None
    assert response.llm_scores.gemini is not None
    assert response.llm_scores.ollama is not None
    assert response.llm_scores.qwen is not None

    # Итоговые метрики
    assert 0 <= response.final_score <= 10
    assert response.score_variance >= 0
    assert 0 <= response.confidence_score <= 1


@pytest.mark.asyncio
async def test_pipeline_partial_llm_failure(mock_local_metrics, mock_llm_judges):
    """Часть LLM судей падает — остальные работают, fallback не срабатывает"""
    mock_llm_judges["gigachat"].return_value = None
    mock_llm_judges["qwen"].return_value = None

    orchestrator = EvaluationOrchestrator()
    request = EvaluateRequest(
        code_snippet="fun greet(name: String): String = \"Hello, $name!\"",
        generated_doc="Greets the user by name"
    )

    response = await orchestrator.evaluate(request)

    # GigaChat и Qwen не ответили
    assert response.llm_scores.gigachat is None
    assert response.llm_scores.qwen is None

    # Gemini и Ollama ответили
    assert response.llm_scores.gemini is not None
    assert response.llm_scores.ollama is not None

    # Финальная оценка всё равно рассчитана через взвешенную формулу
    assert response.final_score > 0
    assert response.confidence_score > 0


@pytest.mark.asyncio
async def test_pipeline_all_llm_fail_uses_fallback(mock_local_metrics, mock_llm_judges):
    """Все LLM падают — fallback на локальные метрики"""
    for judge in mock_llm_judges.values():
        judge.return_value = None

    orchestrator = EvaluationOrchestrator()
    request = EvaluateRequest(
        code_snippet="val PI = 3.14159",
        generated_doc="Constant PI value"
    )

    response = await orchestrator.evaluate(request)

    # Fallback: (sem + cov + readability) / 3 = (0.8 + 9.0 + 7.5) / 3 ≈ 5.77
    expected_fallback = round((0.8 + 9.0 + 7.5) / 3, 2)
    assert response.final_score == expected_fallback
    assert response.confidence_score == 0.0


@pytest.mark.asyncio
async def test_pipeline_llm_exceptions_handled(mock_local_metrics, mock_llm_judges):
    """LLM судьи бросают исключения — они перехватываются, pipeline не ломается"""
    mock_llm_judges["gigachat"].side_effect = ConnectionError("Connection refused")
    mock_llm_judges["gemini"].side_effect = TimeoutError("Timed out")
    # ollama и qwen работают нормально

    orchestrator = EvaluationOrchestrator()
    request = EvaluateRequest(
        code_snippet="fun sum(a: Int, b: Int): Int = a + b",
        generated_doc="Adds two integers together"
    )

    response = await orchestrator.evaluate(request)

    assert response.llm_scores.gigachat is None
    assert response.llm_scores.gemini is None
    assert response.llm_scores.ollama is not None
    assert response.llm_scores.qwen is not None
    assert response.final_score > 0


@pytest.mark.asyncio
async def test_pipeline_weights_applied_correctly(mock_local_metrics, mock_llm_judges):
    """Проверяем что веса корректно применяются в финальной формуле"""
    # Устанавливаем предсказуемые значения
    mock_local_metrics.calculate_semantic_similarity.return_value = 5.0
    mock_local_metrics.calculate_coverage.return_value = 5.0
    mock_local_metrics.calculate_readability.return_value = 5.0

    # Все LLM возвращают одинаковое значение — дисперсия = 0
    for judge in mock_llm_judges.values():
        judge.return_value = 5.0

    orchestrator = EvaluationOrchestrator()
    request = EvaluateRequest(
        code_snippet="fun identity(x: Any): Any = x",
        generated_doc="Returns the input value unchanged"
    )

    response = await orchestrator.evaluate(request)

    # Все метрики = 5.0, все веса дают 5.0: 5*0.15 + 5*0.15 + 5*0.1 + 5*0.6 = 5.0
    assert response.final_score == 5.0
    assert response.score_variance == 0.0
    assert response.confidence_score == 1.0
