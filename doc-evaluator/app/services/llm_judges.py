import re
import asyncio
from abc import ABC, abstractmethod
from gigachat import GigaChat
import google.generativeai as genai
from openai import AsyncOpenAI

from app.core.config import get_settings

settings = get_settings()

JUDGE_PROMPT = """
Ты — Senior Technical Writer. Оцени качество документации.
Код:
    {code}
Документация:
    {doc}

Оцени по шкале от 0 до 10. Верни ТОЛЬКО число.
"""

class BaseJudge(ABC):
    @abstractmethod
    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        pass

    def _extract_score(self, text: str) -> float | None:
        """Парсит число из ответа LLM"""
        try:
            match = re.search(r'\d+(\.\d+)?', text)
            if match:
                score = float(match.group())
                return min(10.0, max(0.0, score))
        except:
            pass
        return None

class GigaChatJudge(BaseJudge):
    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        if not settings.GIGACHAT_CREDENTIALS: return None
        try:
            async with GigaChat(credentials=settings.GIGACHAT_CREDENTIALS, verify_ssl_certs=False, temperature=temperature) as giga:
                response = await giga.achat(JUDGE_PROMPT.format(code=code, doc=doc))
                return self._extract_score(response.choices[0].message.content)
        except Exception as e:
            print(f"GigaChat Error: {e}")
            return None

class GeminiJudge(BaseJudge):
    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        if not settings.GEMINI_API_KEY: return None
        try:
            genai.configure(api_key=settings.GEMINI_API_KEY)
            model = genai.GenerativeModel('gemini-1.5-flash')
            # Gemini sync lib wrapper in thread
            response = await asyncio.to_thread(
                model.generate_content,
                JUDGE_PROMPT.format(code=code, doc=doc),
                generation_config=genai.types.GenerationConfig(temperature=temperature)
            )
            return self._extract_score(response.text)
        except Exception as e:
            print(f"Gemini Error: {e}")
            return None

class OllamaJudge(BaseJudge):
    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        try:
            client = AsyncOpenAI(base_url=settings.OLLAMA_HOST, api_key="ollama")
            response = await client.chat.completions.create(
                model=settings.OLLAMA_MODEL,
                messages=[{"role": "user", "content": JUDGE_PROMPT.format(code=code, doc=doc)}],
                temperature=temperature
            )
            return self._extract_score(response.choices[0].message.content)
        except Exception as e:
            print(f"Ollama Error: {e}")
            return None