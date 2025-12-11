import re
import textstat
from sentence_transformers import SentenceTransformer, util

class LocalMetricsService:
    _instance = None  # 1. Храним единственный экземпляр здесь

    def __init__(self):
        print("Loading CodeBERT model... (CPU)")
        self.embedder = SentenceTransformer('microsoft/codebert-base')
        print("Model loaded.")

    @classmethod
    def get_instance(cls):
        """
        Паттерн Singleton.
        Если экземпляр уже есть - возвращаем его.
        Если нет - создаем (и в этот момент грузится модель).
        """
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    def calculate_semantic_similarity(self, code: str, doc: str) -> float:
        embeddings = self.embedder.encode([code, doc], convert_to_tensor=True)
        score = util.cos_sim(embeddings[0], embeddings[1]).item()
        return max(0.0, min(1.0, score)) * 10

    def calculate_coverage(self, code: str, doc: str) -> float:
        tokens = re.findall(r'\b[a-zA-Z_][a-zA-Z0-9_]*\b', code)
        stopwords = {'val', 'var', 'fun', 'return', 'class', 'override', 'private', 'public', 'import', 'package'}
        keywords = {t for t in tokens if len(t) > 3 and t not in stopwords}

        if not keywords:
            return 10.0

        doc_lower = doc.lower()
        found = sum(1 for t in keywords if t.lower() in doc_lower)
        return (found / len(keywords)) * 10

    def calculate_readability(self, doc: str) -> float:
        try:
            score = textstat.flesch_reading_ease(doc)
            return max(0.0, min(100.0, score)) / 10.0
        except:
            return 5.0