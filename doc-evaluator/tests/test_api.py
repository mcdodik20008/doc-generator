from app.schemas.evaluation import EvaluateResponse

def test_evaluate_endpoint(client, mock_local_metrics, mock_llm_judges):
    response = client.post(
        "/evaluate",
        json={
            "code_snippet": "def foo(): pass",
            "generated_doc": "Documentation for foo"
        }
    )
    
    assert response.status_code == 200
    data = response.json()
    
    # Проверяем структуру ответа
    assert "semantic_score" in data
    assert "keyword_coverage" in data
    assert "readability_score" in data
    assert "llm_scores" in data
    assert "final_score" in data
    assert "score_variance" in data
    assert "confidence_score" in data
    
    # Проверяем значения (должны быть из моков)
    assert data["final_score"] > 0

def test_evaluate_endpoint_validation_error(client):
    response = client.post(
        "/evaluate",
        json={
            "code_snippet": "short", # min_length=5
            "generated_doc": "doc"
        }
    )
    assert response.status_code == 422
