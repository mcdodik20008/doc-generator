import pytest
from app.services.local_metrics import LocalMetricsService

# Поскольку LocalMetricsService загружает модели в __init__, 
# мы можем либо замокать модели, либо протестировать реальную загрузку (долго).
# Для юнит-тестов лучше замокать модели.
# Но здесь мы хотим проверить логику calculate_coverage и calculate_readability,
# которые не зависят от тяжелых моделей (кроме calculate_semantic_similarity).

@pytest.fixture
def local_service(monkeypatch):
    # Патчим __init__, чтобы не грузить модели
    monkeypatch.setattr(LocalMetricsService, "__init__", lambda self: None)
    # Создаем экземпляр без вызова реального __init__
    service = LocalMetricsService.__new__(LocalMetricsService)
    # Инициализируем нужные поля, если надо (для coverage и readability не надо)
    # Для semantic_similarity нужны embedder и cross_encoder, их замокаем отдельно если будем тестить
    return service

def test_calculate_coverage(local_service):
    code = "def hello_world(): print('hello')"
    doc = "This function prints hello world"
    
    # tokens: hello_world, print, hello
    # keywords: hello, world (len>3, not in exclude) -> hello, world?
    # wait, 'hello_world' -> split? No, regex is \b[a-zA-Z_][a-zA-Z0-9_]*\b
    # tokens: def, hello_world, print, hello
    # keywords: hello_world, print, hello (def excluded? no, but len=3 excluded? yes len>3)
    # 'def' len=3 -> excluded
    # 'print' len=5 -> included
    # 'hello' len=5 -> included
    # 'hello_world' len=11 -> included
    
    # doc: "This function prints hello world"
    # 'print' in doc? 'prints' contains 'print' -> yes
    # 'hello' in doc? yes
    # 'hello_world' in doc? no
    
    # expected: 2/3 found -> ~6.66
    
    score = local_service.calculate_coverage(code, doc)
    assert 0 <= score <= 10
    assert score > 0 # Should find something

def test_calculate_coverage_empty(local_service):
    code = "a = 1"
    doc = "nothing"
    # no keywords > 3 chars
    score = local_service.calculate_coverage(code, doc)
    assert score == 10.0 # If no keywords, return 10.0

def test_calculate_readability(local_service):
    doc = "Simple text. Very easy."
    score = local_service.calculate_readability(doc)
    assert 0 <= score <= 10
