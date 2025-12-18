"""
Демонстрация использования DocEvaluatorClient.

Этот скрипт показывает различные сценарии использования клиента
для оценки качества документации.

Требования:
    - Сервис должен быть запущен: python -m uvicorn app.main:app --reload
"""
import asyncio
import logging
from app.client import DocEvaluatorClient, ServiceUnavailableError, ValidationError


# Настройка логирования для демо
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


# Тестовые сценарии
TEST_SCENARIOS = [
    {
        "name": "Simple Function",
        "code": """def calculate_sum(a, b):
    return a + b""",
        "doc": "This function calculates the sum of two numbers."
    },
    {
        "name": "Class with Methods",
        "code": """class User:
    def __init__(self, name, email):
        self.name = name
        self.email = email
    
    def get_info(self):
        return f'{self.name} ({self.email})'""",
        "doc": """User class represents a user in the system.
        
        Attributes:
            name: User's full name
            email: User's email address
        
        Methods:
            get_info: Returns formatted user information
        """
    },
    {
        "name": "Complex Algorithm",
        "code": """def bubble_sort(arr):
    n = len(arr)
    for i in range(n):
        for j in range(0, n - i - 1):
            if arr[j] > arr[j + 1]:
                arr[j], arr[j + 1] = arr[j + 1], arr[j]
    return arr""",
        "doc": """Implements bubble sort algorithm.
        
        This function sorts an array in ascending order using the bubble sort
        technique. It has O(n²) time complexity.
        
        Args:
            arr: List of comparable elements
        
        Returns:
            Sorted list
        """
    },
    {
        "name": "Edge Case - Minimal Valid Input",
        "code": "x = 5",
        "doc": "Variable x"
    }
]


async def run_scenario(client: DocEvaluatorClient, scenario: dict):
    """Запускает один тестовый сценарий."""
    print(f"\n{'='*60}")
    print(f"🔍 Scenario: {scenario['name']}")
    print(f"{'='*60}")
    
    try:
        result = await client.evaluate(
            code_snippet=scenario["code"],
            generated_doc=scenario["doc"]
        )
        
        print(f"\n✅ Evaluation Result:")
        print(f"   Final Score:     {result.final_score:.1f}/10")
        print(f"   Confidence:      {result.confidence_score:.2f}")
        print(f"   Variance:        {result.score_variance:.3f}")
        print(f"\n📊 Detailed Scores:")
        print(f"   Semantic:        {result.semantic_score:.2f}/10")
        print(f"   Coverage:        {result.keyword_coverage:.2f}/10")
        print(f"   Readability:     {result.readability_score:.2f}")
        print(f"\n🤖 LLM Scores:")
        print(f"   GigaChat:        {result.llm_scores.gigachat or 'N/A'}")
        print(f"   Gemini:          {result.llm_scores.gemini or 'N/A'}")
        print(f"   Ollama:          {result.llm_scores.ollama or 'N/A'}")
        
        return True
    
    except ValidationError as e:
        print(f"\n❌ Validation Error: {e}")
        return False
    
    except Exception as e:
        print(f"\n❌ Error: {e}")
        return False


async def main():
    """Главная функция демонстрации."""
    print("="*60)
    print("📝 Doc Evaluator Client - Demo")
    print("="*60)
    
    try:
        # Используем context manager для автоматического управления ресурсами
        async with DocEvaluatorClient(log_level=logging.INFO) as client:
            # Проверка здоровья сервиса
            print("\n🏥 Checking service health...")
            health = await client.health_check()
            print(f"   Status: {health.get('status', 'unknown')}")
            print(f"   Service: {health.get('service', 'unknown')}")
            
            # Запуск всех сценариев
            results = []
            for scenario in TEST_SCENARIOS:
                success = await run_scenario(client, scenario)
                results.append((scenario["name"], success))
            
            # Итоговая статистика
            print(f"\n{'='*60}")
            print("📈 Summary")
            print(f"{'='*60}")
            successful = sum(1 for _, success in results if success)
            total = len(results)
            print(f"   Total scenarios: {total}")
            print(f"   Successful:      {successful}")
            print(f"   Failed:          {total - successful}")
            print(f"   Success rate:    {successful/total*100:.1f}%")
            
    except ServiceUnavailableError:
        print("\n❌ Service is not available!")
        print("   Please start the service first:")
        print("   > .venv\\Scripts\\python.exe -m uvicorn app.main:app --reload")
        return 1
    
    except KeyboardInterrupt:
        print("\n\n⚠️  Interrupted by user")
        return 1
    
    except Exception as e:
        logger.exception("Unexpected error occurred")
        print(f"\n❌ Unexpected error: {e}")
        return 1
    
    print(f"\n{'='*60}")
    print("✅ Demo completed successfully!")
    print(f"{'='*60}\n")
    return 0


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    exit(exit_code)
