import pytest


def test_missing_code_snippet(client):
    """Запрос без code_snippet должен вернуть 422"""
    response = client.post(
        "/evaluate",
        json={"generated_doc": "Some documentation text"}
    )
    assert response.status_code == 422


def test_missing_generated_doc(client):
    """Запрос без generated_doc должен вернуть 422"""
    response = client.post(
        "/evaluate",
        json={"code_snippet": "def hello(): pass"}
    )
    assert response.status_code == 422


def test_empty_json_body(client):
    """Пустой JSON должен вернуть 422"""
    response = client.post("/evaluate", json={})
    assert response.status_code == 422


def test_code_snippet_too_short(client):
    """Код короче 5 символов должен вернуть 422"""
    response = client.post(
        "/evaluate",
        json={"code_snippet": "ab", "generated_doc": "Valid documentation text"}
    )
    assert response.status_code == 422


def test_generated_doc_too_short(client):
    """Документация короче 5 символов должна вернуть 422"""
    response = client.post(
        "/evaluate",
        json={"code_snippet": "def hello(): pass", "generated_doc": "ab"}
    )
    assert response.status_code == 422


def test_unicode_input(client, mock_local_metrics, mock_llm_judges):
    """Юникод в коде и документации должен обрабатываться корректно"""
    response = client.post(
        "/evaluate",
        json={
            "code_snippet": "fun приветМир(): String = \"Привет, мир!\"",
            "generated_doc": "Функция возвращает строку приветствия на русском языке"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert data["final_score"] >= 0
    assert data["final_score"] <= 10


def test_large_code_snippet(client, mock_local_metrics, mock_llm_judges):
    """Большой код должен обрабатываться без ошибок"""
    large_code = "fun bigFunction() {\n" + "    val x = 1\n" * 500 + "}"
    response = client.post(
        "/evaluate",
        json={
            "code_snippet": large_code,
            "generated_doc": "A function that creates many variables"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert 0 <= data["final_score"] <= 10


def test_special_characters_in_code(client, mock_local_metrics, mock_llm_judges):
    """Спецсимволы в коде не должны ломать оценку"""
    response = client.post(
        "/evaluate",
        json={
            "code_snippet": 'fun escape(): String = "line1\\nline2\\t\\"quoted\\""',
            "generated_doc": "Returns a string with escaped newlines and quotes"
        }
    )
    assert response.status_code == 200


def test_response_score_bounds(client, mock_local_metrics, mock_llm_judges):
    """Все score должны быть в допустимых диапазонах"""
    response = client.post(
        "/evaluate",
        json={
            "code_snippet": "def calculate(a, b): return a + b",
            "generated_doc": "Calculates the sum of two numbers a and b"
        }
    )
    assert response.status_code == 200
    data = response.json()

    assert 0 <= data["semantic_score"] <= 10
    assert 0 <= data["keyword_coverage"] <= 10
    assert 0 <= data["final_score"] <= 10
    assert data["score_variance"] >= 0
    assert 0 <= data["confidence_score"] <= 1


def test_all_llm_fail_fallback(client, mock_local_metrics, mock_llm_judges):
    """Если все LLM вернут None, должен сработать fallback на локальные метрики"""
    for judge in mock_llm_judges.values():
        judge.return_value = None

    response = client.post(
        "/evaluate",
        json={
            "code_snippet": "def hello(): return 'hello'",
            "generated_doc": "Returns hello string"
        }
    )
    assert response.status_code == 200
    data = response.json()

    # Fallback: (sem + cov + readability) / 3 = (0.8 + 9.0 + 7.5) / 3 ≈ 5.77
    assert data["final_score"] > 0
    assert data["confidence_score"] == 0.0  # Нет LLM — нет уверенности
    assert data["llm_scores"]["gigachat"] is None
    assert data["llm_scores"]["gemini"] is None
    assert data["llm_scores"]["ollama"] is None
    assert data["llm_scores"]["qwen"] is None


def test_invalid_content_type(client):
    """Неправильный Content-Type должен вернуть ошибку"""
    response = client.post(
        "/evaluate",
        content=b"not json",
        headers={"Content-Type": "text/plain"}
    )
    assert response.status_code == 422


def test_extra_fields_ignored(client, mock_local_metrics, mock_llm_judges):
    """Лишние поля в запросе должны игнорироваться"""
    response = client.post(
        "/evaluate",
        json={
            "code_snippet": "def foo(): pass",
            "generated_doc": "Documentation for foo",
            "extra_field": "should be ignored",
            "another_extra": 42
        }
    )
    assert response.status_code == 200
