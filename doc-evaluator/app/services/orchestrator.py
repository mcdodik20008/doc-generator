from app.core.config import get_settings

settings = get_settings()

class EvaluationOrchestrator:
    def __init__(self):
        # Инициализируем локальные метрики (загрузятся при первом вызове, если еще не загружены)
        self.local = LocalMetricsService.get_instance()
        # Инициализируем судей
import asyncio
from app.schemas.evaluation import EvaluateRequest, EvaluateResponse, LlmScores
from app.services.local_metrics import LocalMetricsService
from app.services.llm_judges import GigaChatJudge, GeminiJudge, OllamaJudge
from app.core.config import get_settings

settings = get_settings()

class EvaluationOrchestrator:
    def __init__(self):
        # Инициализируем локальные метрики (загрузятся при первом вызове, если еще не загружены)
        self.local = LocalMetricsService.get_instance()
        # Инициализируем судей
        self.judges = [
            ("gigachat", GigaChatJudge()),
            ("gemini", GeminiJudge()),
            ("ollama", OllamaJudge())
        ]

    async def evaluate(self, request: EvaluateRequest) -> EvaluateResponse:
        code = request.code_snippet
        doc = request.generated_doc

        # 1. Локальные метрики (CPU bound, но быстрые)
        sem_score = self.local.calculate_semantic_similarity(code, doc)
        coverage_score = self.local.calculate_coverage(code, doc)
        readability_score = self.local.calculate_readability(doc)

        # 2. LLM метрики (IO bound - запускаем параллельно)
        # Self-Consistency: запускаем N раундов с разной температурой
        rounds = settings.SELF_CONSISTENCY_ROUNDS
        all_tasks = []
        
        # Генерируем температуры от 0.1 до 0.7
        temperatures = [0.1 + (i * 0.2) for i in range(rounds)] # 0.1, 0.3, 0.5...

        for temp in temperatures:
            for name, judge in self.judges:
                all_tasks.append((name, judge.evaluate(code, doc, temperature=temp)))

        # Запускаем все задачи
        # all_tasks - это список кортежей (name, coroutine), нам нужно запустить корутины
        judge_names_flat = [t[0] for t in all_tasks]
        coroutines = [t[1] for t in all_tasks]
        
        results_flat = await asyncio.gather(*coroutines)

        # Собираем результаты по судьям
        # scores_by_judge = {"gigachat": [8, 9, 8], ...}
        scores_by_judge = {name: [] for name, _ in self.judges}
        
        for name, score in zip(judge_names_flat, results_flat):
            if score is not None:
                scores_by_judge[name].append(score)

        # Считаем среднее для каждого судьи
        final_llm_scores_map = {}
        all_valid_scores = []
        
        for name, scores in scores_by_judge.items():
            if scores:
                avg = sum(scores) / len(scores)
                final_llm_scores_map[name] = avg
                all_valid_scores.extend(scores)
            else:
                final_llm_scores_map[name] = None

        # Считаем общую дисперсию всех оценок LLM
        import statistics
        if len(all_valid_scores) > 1:
            variance = statistics.variance(all_valid_scores)
        else:
            variance = 0.0
            
        # Уверенность: чем меньше дисперсия, тем выше уверенность.
        # Если дисперсия 0 -> уверенность 1. Если дисперсия 10 -> уверенность ~0.
        # Формула: 1 / (1 + variance)
        confidence = 1.0 / (1.0 + variance)

        # Среднее по всем LLM
        avg_llm_score = sum(all_valid_scores) / len(all_valid_scores) if all_valid_scores else 0.0

        # Если LLM недоступны, откатываемся только на локальные веса
        if not all_valid_scores:
            final = (sem_score + coverage_score) / 2
            confidence = 0.0 # Нет LLM - нет уверенности в их оценке
        else:
            final = (
                    (sem_score * settings.WEIGHT_SEMANTIC) +
                    (coverage_score * settings.WEIGHT_COVERAGE) +
                    (avg_llm_score * settings.WEIGHT_LLM)
            )

        return EvaluateResponse(
            semantic_score=round(sem_score, 2),
            keyword_coverage=round(coverage_score, 2),
            readability_score=round(readability_score, 2),
            llm_scores=LlmScores(
                gigachat=final_llm_scores_map.get("gigachat"),
                gemini=final_llm_scores_map.get("gemini"),
                ollama=final_llm_scores_map.get("ollama")
            ),
            final_score=round(final, 2),
            score_variance=round(variance, 2),
            confidence_score=round(confidence, 2)
        )