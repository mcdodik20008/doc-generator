from pydantic import BaseModel, Field

class EvaluateRequest(BaseModel):
    code_snippet: str = Field(..., min_length=5, description="Исходный код функции/класса")
    generated_doc: str = Field(..., min_length=5, description="Сгенерированная документация")

class LlmScores(BaseModel):
    gigachat: float | None = None
    gemini: float | None = None
    ollama: float | None = None

class EvaluateResponse(BaseModel):
    # Локальные метрики
    # ge = greater or equal (>=), le = less or equal (<=)
    semantic_score: float = Field(..., ge=0, le=10, description="Сходство векторов (0-10)")
    keyword_coverage: float = Field(..., ge=0, le=10, description="Оценка покрытия ключевыми словами (0-10)")
    readability_score: float = Field(..., description="Читаемость текста")

    # LLM метрики
    llm_scores: LlmScores

    # Итог
    final_score: float = Field(..., ge=0, le=10, description="Взвешенная оценка 0-10")

    score_variance: float = Field(..., ge=0, description="Дисперсия не может быть отрицательной")

    confidence_score: float = Field(..., ge=0, le=1, description="Уверенность в оценке (0-1)")