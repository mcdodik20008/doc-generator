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
    semantic_score: float = Field(..., description="Сходство векторов (CodeBERT)")
    keyword_coverage: float = Field(..., description="Процент покрытия переменных")
    readability_score: float = Field(..., description="Читаемость текста")

    # LLM метрики
    llm_scores: LlmScores

    # Итог
    final_score: float = Field(..., description="Взвешенная оценка 0-10")
    score_variance: float = Field(..., description="Дисперсия оценок LLM")
    confidence_score: float = Field(..., description="Уверенность в оценке (0-1)")