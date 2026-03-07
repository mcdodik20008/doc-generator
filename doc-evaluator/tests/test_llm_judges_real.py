"""
Реальные интеграционные тесты для LLM-судей.
Требуют запущенные сервисы и настроенные credentials.
Запускать отдельно: pytest tests/test_llm_judges_real.py -v -s
"""
import pytest
from app.services.llm_judges import GigaChatJudge, GeminiJudge, OllamaJudge, QwenJudge
from app.core.config import get_settings

CODE_SAMPLE = """
def add(a, b):
    return a + b
"""
DOC_SAMPLE = """
Function to add two numbers.
Args:
    a: First number
    b: Second number
Returns:
    Sum of a and b.
"""


@pytest.mark.asyncio
async def test_gigachat_real_call():
    """Test GigaChat with real credentials."""
    judge = GigaChatJudge()
    print("\n[GigaChat] Sending request...")
    score = await judge.evaluate(CODE_SAMPLE, DOC_SAMPLE)
    print(f"[GigaChat] Score received: {score}")

    assert score is not None, "GigaChat should return a score"
    assert isinstance(score, float), "Score should be a float"
    assert 0 <= score <= 10, "Score should be between 0 and 10"


@pytest.mark.asyncio
async def test_gemini_real_call():
    """Test Gemini with real API key."""
    judge = GeminiJudge()
    print("\n[Gemini] Sending request...")
    score = await judge.evaluate(CODE_SAMPLE, DOC_SAMPLE)
    print(f"[Gemini] Score received: {score}")

    assert score is not None, "Gemini should return a score"
    assert isinstance(score, float), "Score should be a float"
    assert 0 <= score <= 10, "Score should be between 0 and 10"


@pytest.mark.asyncio
async def test_ollama_real_call():
    """Test Ollama with real host."""
    conf = get_settings()
    print(f"\nDEBUG: Loaded Host: '{conf.OLLAMA_HOST}'")
    print(f"DEBUG: Loaded Model: '{conf.OLLAMA_MODEL}'")

    judge = OllamaJudge()
    print("\n[Ollama] Sending request...")
    score = await judge.evaluate(CODE_SAMPLE, DOC_SAMPLE)

    assert score is not None, "Ollama should return a score (check if Ollama is running)"
    assert isinstance(score, float), "Score should be a float"
    assert 0 <= score <= 10, "Score should be between 0 and 10"


@pytest.mark.asyncio
async def test_qwen_real_call():
    """Test Qwen with real API key."""
    judge = QwenJudge()
    print("\n[Qwen] Sending request...")
    score = await judge.evaluate(CODE_SAMPLE, DOC_SAMPLE)
    print(f"[Qwen] Score received: {score}")

    assert score is not None, "Qwen should return a score"
    assert isinstance(score, float), "Score should be a float"
    assert 0 <= score <= 10, "Score should be between 0 and 10"


@pytest.mark.asyncio
async def test_get_settings():
    """Verify settings load correctly."""
    settings = get_settings()
    print(f"\n[Settings] {settings}")
    assert settings.OLLAMA_HOST.startswith("http")
    assert settings.OLLAMA_MODEL
    assert settings.GEMINI_MODEL
