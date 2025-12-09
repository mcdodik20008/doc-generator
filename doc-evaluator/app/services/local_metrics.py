import re
import textstat
from sentence_transformers import SentenceTransformer, util, CrossEncoder

class LocalMetricsService:
    _instance = None

    def __init__(self):
        print("Loading ML models... (CPU)")
        # Загружаем модели один раз при старте
        self.embedder = SentenceTransformer('sentence-transformers/stsb-roberta-base-v2')
        self.cross_encoder = CrossEncoder('cross-encoder/stsb-distilroberta-base')
        print("ML models loaded.")

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def calculate_semantic_similarity(self, code: str, doc: str) -> float:
        """Векторное сходство (0-10)"""
        embeddings = self.embedder.encode([code, doc], convert_to_tensor=True)
        score = util.cos_sim(embeddings[0], embeddings[1]).item()
        return max(0.0, min(1.0, score)) * 10

    def calculate_coverage(self, code: str, doc: str) -> float:
        """Лексическое покрытие (0-10)"""
        # Простая регулярка для токенов
        tokens = re.findall(r'\b[a-zA-Z_][a-zA-Z0-9_]*\b', code)
        # Фильтруем мусор
        keywords = {t for t in tokens if len(t) > 3 and t not in {'val', 'var', 'fun', 'return', 'class'}}

        if not keywords:
            return 10.0

        doc_lower = doc.lower()
        found = sum(1 for t in keywords if t.lower() in doc_lower)
        return (found / len(keywords)) * 10

    def calculate_readability(self, doc: str) -> float:
        """Читаемость (0-10)"""
        try:
            score = textstat.flesch_reading_ease(doc)
            return max(0.0, min(100.0, score)) / 10.0
        except:
            return 5.0