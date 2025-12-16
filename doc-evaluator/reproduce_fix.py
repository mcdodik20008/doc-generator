from app.schemas.evaluation import EvaluateResponse, LlmScores

print("Attempting to create EvaluateResponse with semantic_score=9.5...")
try:
    resp = EvaluateResponse(
        semantic_score=9.5,
        keyword_coverage=10.0,
        readability_score=5.0,
        llm_scores=LlmScores(),
        final_score=8.5,
        score_variance=0.0,
        confidence_score=1.0
    )
    print("SUCCESS: Validation passed!")
except Exception as e:
    print(f"FAILURE: Validation failed: {e}")
