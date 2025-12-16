import re
import asyncio
from abc import ABC, abstractmethod
from gigachat import GigaChat
import google.generativeai as genai
import aiohttp
from app.core.config import get_settings
import dashscope
from http import HTTPStatus

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
            async with GigaChat(credentials=settings.GIGACHAT_CREDENTIALS, verify_ssl_certs=False) as giga:
                response = await giga.achat(JUDGE_PROMPT.format(code=code, doc=doc), temperature=temperature)
                return self._extract_score(response.choices[0].message.content)
        except Exception as e:
            print(f"GigaChat Error ({type(e).__name__}): {e}")
            import traceback
            traceback.print_exc()
            return None

class GeminiJudge(BaseJudge):
    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        if not settings.GEMINI_API_KEY: return None
        try:
            genai.configure(api_key=settings.GEMINI_API_KEY)
            model = genai.GenerativeModel('gemini-1.5-flash')
            response = await asyncio.to_thread(
                model.generate_content,
                JUDGE_PROMPT.format(code=code, doc=doc),
                generation_config=genai.types.GenerationConfig(temperature=temperature)
            )
            return self._extract_score(response.text)
        except Exception as e:
            print(f"Gemini Error ({type(e).__name__}): {e}")
            import traceback
            traceback.print_exc()
            return None

class OllamaJudge(BaseJudge):
    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        url = "http://127.0.0.1:11434/api/chat"

        payload = {
            "model": settings.OLLAMA_MODEL, # 'qwen2.5:0.5b'
            "messages": [
                {"role": "user", "content": JUDGE_PROMPT.format(code=code, doc=doc)}
            ],
            "stream": False,
            "options": {
                "temperature": temperature
            }
        }

        try:
            async with aiohttp.ClientSession() as session:
                async with session.post(url, json=payload) as response:
                    if response.status != 200:
                        error_text = await response.text()
                        print(f"Ollama Error {response.status}: {error_text}")
                        return None

                    result = await response.json()
                    content = result.get("message", {}).get("content", "")

                    return self._extract_score(content)

        except Exception as e:
            print(f"Ollama Error ({type(e).__name__}): {e}")
            import traceback
            traceback.print_exc()
            return None

class QwenJudge(BaseJudge):
    async def evaluate(self, code: str, doc: str) -> float | None:
        if not settings.QWEN_API_KEY: return None
        dashscope.api_key = settings.QWEN_API_KEY
        try:
            response = dashscope.Generation.call(
                model=settings.QWEN_MODEL, # Или 'qwen-plus' (поумнее)
                messages=[
                    {'role': 'user', 'content': JUDGE_PROMPT.format(code=code, doc=doc)}
                ],
                result_format='message',
            )

            if response.status_code == HTTPStatus.OK:
                content = response.output.choices[0]['message']['content']
                return self._extract_score(content)
            else:
                print(f"Qwen Error: {response.code} - {response.message}")
                return None

        except Exception as e:
            print(f"Connection Error: {e}")
            return None