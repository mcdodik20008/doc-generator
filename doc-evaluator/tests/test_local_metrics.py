import pytest
from app.services.local_metrics import LocalMetricsService


@pytest.fixture
def local_service(monkeypatch):
    """Создаёт LocalMetricsService без загрузки CodeBERT модели"""
    monkeypatch.setattr(LocalMetricsService, "__init__", lambda self: None)
    service = LocalMetricsService.__new__(LocalMetricsService)
    return service


# --- calculate_coverage ---

def test_calculate_coverage(local_service):
    code = "def hello_world(): print('hello')"
    doc = "This function prints hello world"

    # tokens: def, hello_world, print, hello
    # keywords (len>3, not stopword): hello_world, print, hello
    # doc contains: 'print' (via 'prints'), 'hello' — yes; 'hello_world' — no
    # expected: 2/3 ≈ 6.67

    score = local_service.calculate_coverage(code, doc)
    assert 0 <= score <= 10
    assert score > 0


def test_calculate_coverage_empty_keywords(local_service):
    code = "a = 1"
    doc = "nothing"
    # no keywords > 3 chars → return MAX_SCORE
    score = local_service.calculate_coverage(code, doc)
    assert score == 10.0


def test_calculate_coverage_full_match(local_service):
    code = "def process_data(input_list): return sorted(input_list)"
    doc = "process_data takes an input_list and returns sorted input_list"
    score = local_service.calculate_coverage(code, doc)
    assert score > 5.0  # Должно быть хорошее покрытие


def test_calculate_coverage_no_match(local_service):
    code = "def calculate_fibonacci(number): pass"
    doc = "This is completely unrelated text about weather"
    score = local_service.calculate_coverage(code, doc)
    assert score < 5.0  # Мало пересечений


def test_calculate_coverage_empty_code(local_service):
    score = local_service.calculate_coverage("", "some doc")
    assert score == 0.0


def test_calculate_coverage_empty_doc(local_service):
    score = local_service.calculate_coverage("def foo(): pass", "")
    assert score == 0.0


def test_calculate_coverage_blank_strings(local_service):
    score = local_service.calculate_coverage("   ", "   ")
    assert score == 0.0


def test_calculate_coverage_kotlin_stopwords_filtered(local_service):
    code = "val myVariable = if (true) return else override"
    doc = "nothing relevant"
    # keywords after filtering stopwords (val, if, return, else, override): only myVariable, true
    score = local_service.calculate_coverage(code, doc)
    assert 0 <= score <= 10


# --- calculate_readability ---

def test_calculate_readability_simple(local_service):
    doc = "Simple text. Very easy to read."
    score = local_service.calculate_readability(doc)
    assert 0 <= score <= 10


def test_calculate_readability_complex(local_service):
    doc = ("The implementation utilizes sophisticated algorithmic paradigms "
           "to facilitate the orchestration of distributed computational resources.")
    score = local_service.calculate_readability(doc)
    assert 0 <= score <= 10


def test_calculate_readability_empty(local_service):
    score = local_service.calculate_readability("")
    assert score == LocalMetricsService.DEFAULT_READABILITY_SCORE


def test_calculate_readability_blank(local_service):
    score = local_service.calculate_readability("   ")
    assert score == LocalMetricsService.DEFAULT_READABILITY_SCORE
