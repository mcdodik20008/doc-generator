import httpx
import asyncio

async def main():
    # URL сервиса (предполагаем, что он запущен локально на порту 8000)
    url = "http://localhost:8000/evaluate"
    
    # Тестовые данные
    payload = {
        "code_snippet": "def calculate_sum(a, b):\n    return a + b",
        "generated_doc": "This function calculates the sum of two numbers."
    }
    
    print(f"Sending request to {url}...")
    
    # trust_env=False отключает использование прокси из переменных окружения
    async with httpx.AsyncClient(trust_env=False) as client:
        try:
            # Сначала проверяем здоровье
            health = await client.get("http://localhost:8000/health", timeout=5.0)
            print(f"Health check: {health.status_code} {health.text}")

            response = await client.post(url, json=payload, timeout=60.0)
            response.raise_for_status()
            
            result = response.json()
            
            print("\n--- Evaluation Result ---")
            print(f"Final Score: {result['final_score']}/10")
            print(f"Confidence: {result['confidence_score']}")
            print(f"Variance: {result['score_variance']}")
            print("\nDetailed Scores:")
            print(f"- Semantic: {result['semantic_score']}")
            print(f"- Coverage: {result['keyword_coverage']}")
            print(f"- Readability: {result['readability_score']}")
            print(f"- LLM Scores: {result['llm_scores']}")
            
        except httpx.HTTPError as e:
            print(f"Error: {e}")

if __name__ == "__main__":
    asyncio.run(main())
