import re
import threading
import textstat
import logging
from sentence_transformers import SentenceTransformer, util
from app.core.config import get_settings

logger = logging.getLogger(__name__)

class LocalMetricsService:
    # Константы для расчета метрик
    MAX_SCORE = 10.0  # Максимальный балл для всех метрик
    MIN_TOKEN_LENGTH = 3  # Минимальная длина токена для учета в coverage
    DEFAULT_READABILITY_SCORE = 5.0  # Средний балл при ошибке расчета readability
    READABILITY_SCALE_FACTOR = 10.0  # Коэффициент масштабирования для Flesch Reading Ease (100 -> 10)

    # Stopwords для фильтрации токенов при расчете coverage
    # Специфичны для Kotlin - ключевые слова языка которые не несут семантическую нагрузку
    # TODO: Рассмотреть поддержку других языков программирования через конфигурацию
    KOTLIN_STOPWORDS = {
        'val', 'var', 'fun', 'return', 'class', 'override',
        'private', 'public', 'import', 'package', 'if', 'else',
        'when', 'for', 'while', 'try', 'catch', 'finally'
    }

    # Regex паттерн для извлечения идентификаторов из кода
    # Предназначен для языков с C-подобным синтаксисом (Java, Kotlin, C#, JavaScript и т.д.)
    # TODO: Рассмотреть поддержку других языков (Python с подчеркиваниями, Lisp с дефисами и т.д.)
    IDENTIFIER_PATTERN = r'\b[a-zA-Z_][a-zA-Z0-9_]*\b'

    _instance = None  # 1. Храним единственный экземпляр здесь
    _lock = threading.Lock()  # Для thread-safe инициализации

    def __init__(self):
        settings = get_settings()
        model_name = settings.CODEBERT_MODEL_NAME
        logger.info(f"Loading CodeBERT model '{model_name}'... (CPU)")
        try:
            self.embedder = SentenceTransformer(model_name)
            logger.info(f"CodeBERT model '{model_name}' loaded successfully")
        except OSError as e:
            logger.error(f"Failed to load model - network or disk issue: {e}")
            raise RuntimeError(
                "Failed to load CodeBERT model. Please check network connectivity and disk space."
            ) from e
        except Exception as e:
            logger.error(f"Unexpected error loading model: {e}")
            raise RuntimeError(
                "Failed to initialize LocalMetricsService due to model loading error"
            ) from e

    @classmethod
    def get_instance(cls):
        """
        Паттерн Singleton с thread-safe инициализацией (double-checked locking).
        Если экземпляр уже есть - возвращаем его.
        Если нет - создаем (и в этот момент грузится модель).
        """
        # Первая проверка без блокировки для производительности
        if cls._instance is None:
            # Блокировка только при первой инициализации
            with cls._lock:
                # Вторая проверка внутри блокировки на случай если другой поток уже создал экземпляр
                if cls._instance is None:
                    cls._instance = cls()
        return cls._instance

    def calculate_semantic_similarity(self, code: str, doc: str) -> float:
        # Валидация входных параметров
        if not code or not doc or not code.strip() or not doc.strip():
            logger.warning("Empty or blank code/doc provided to calculate_semantic_similarity")
            return 0.0

        try:
            embeddings = self.embedder.encode([code, doc], convert_to_tensor=True)
            score = util.cos_sim(embeddings[0], embeddings[1]).item()
            return max(0.0, min(1.0, score)) * self.MAX_SCORE
        except Exception as e:
            logger.error(f"Failed to calculate semantic similarity: {e}")
            return 0.0

    def calculate_coverage(self, code: str, doc: str) -> float:
        # Валидация входных параметров
        if not code or not doc or not code.strip() or not doc.strip():
            logger.warning("Empty or blank code/doc provided to calculate_coverage")
            return 0.0

        try:
            tokens = re.findall(self.IDENTIFIER_PATTERN, code)
            keywords = {t for t in tokens if len(t) > self.MIN_TOKEN_LENGTH and t not in self.KOTLIN_STOPWORDS}

            if not keywords:
                return self.MAX_SCORE

            doc_lower = doc.lower()
            # TODO: Простой поиск подстроки может давать ложные совпадения (например, 'user' найдется в 'users')
            found = sum(1 for t in keywords if t.lower() in doc_lower)
            return (found / len(keywords)) * self.MAX_SCORE
        except Exception as e:
            logger.error(f"Failed to calculate coverage: {e}")
            return 0.0

    def calculate_readability(self, doc: str) -> float:
        # Валидация входных параметров
        if not doc or not doc.strip():
            logger.warning("Empty or blank doc provided to calculate_readability")
            return self.DEFAULT_READABILITY_SCORE

        try:
            # TODO: textstat.flesch_reading_ease может работать некорректно для не-английского текста
            score = textstat.flesch_reading_ease(doc)
            return max(0.0, min(100.0, score)) / self.READABILITY_SCALE_FACTOR
        except Exception as e:
            logger.warning(f"Failed to calculate readability score: {e}")
            return self.DEFAULT_READABILITY_SCORE