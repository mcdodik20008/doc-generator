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

    # Проверяем наличие всех судей
    llm = data["llm_scores"]
    assert "gigachat" in llm
    assert "gemini" in llm
    assert "ollama" in llm
    assert "qwen" in llm

    assert data["final_score"] > 0


def test_evaluate_endpoint_validation_error(client):
    response = client.post(
        "/evaluate",
        json={
            "code_snippet": "ab",  # min_length=5
            "generated_doc": "doc"  # min_length=5
        }
    )
    assert response.status_code == 422


def test_health_check(client):
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["service"] == "Doc Evaluator"
