import re
import asyncio
import logging
from abc import ABC, abstractmethod
from gigachat import GigaChat
import google.generativeai as genai
import aiohttp
from app.core.config import get_settings
import dashscope
from http import HTTPStatus

settings = get_settings()
logger = logging.getLogger(__name__)

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
        except (ValueError, AttributeError) as e:
            logger.warning(f"Failed to extract score from text: {e}")
        return None

class GigaChatJudge(BaseJudge):
    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        if not settings.GIGACHAT_CREDENTIALS: return None
        try:
            from gigachat.models import Chat, Messages, MessagesRole

            async with GigaChat(
                credentials=settings.GIGACHAT_CREDENTIALS,
                verify_ssl_certs=settings.GIGACHAT_VERIFY_SSL
            ) as giga:
                payload = Chat(
                    messages=[
                        Messages(
                            role=MessagesRole.USER,
                            content=JUDGE_PROMPT.format(code=code, doc=doc)
                        )
                    ],
                    temperature=temperature
                )
                response = await giga.achat(payload)
                return self._extract_score(response.choices[0].message.content)
        except Exception as e:
            logger.error(f"GigaChat Error: {e}", exc_info=True)
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
            logger.error(f"Gemini Error: {e}", exc_info=True)
            return None

class OllamaJudge(BaseJudge):
    # Константы для конфигурации
    REQUEST_TIMEOUT = 30  # секунд

    def __init__(self):
        # Создаем ClientSession один раз для переиспользования
        timeout = aiohttp.ClientTimeout(total=self.REQUEST_TIMEOUT)
        self._session = aiohttp.ClientSession(timeout=timeout)

    async def close(self):
        """Закрывает ClientSession. Должен вызываться при завершении работы."""
        if self._session and not self._session.closed:
            await self._session.close()

    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        # Валидация входных параметров
        if not code or not doc:
            logger.warning("Empty code or doc in OllamaJudge.evaluate")
            return None

        # Убираем /v1 из OLLAMA_HOST если есть, и строим правильный URL
        base_url = settings.OLLAMA_HOST.rstrip('/').replace('/v1', '')
        url = f"{base_url}/api/chat"

        payload = {
            "model": settings.OLLAMA_MODEL,
            "messages": [
                {"role": "user", "content": JUDGE_PROMPT.format(code=code, doc=doc)}
            ],
            "stream": False,
            "options": {
                "temperature": temperature
            }
        }

        try:
            # Используем переиспользуемую сессию вместо создания новой на каждый запрос
            async with self._session.post(url, json=payload) as response:
                if response.status != 200:
                    error_text = await response.text()
                    logger.error(f"Ollama Error {response.status}: {error_text}")
                    return None

                result = await response.json()
                content = result.get("message", {}).get("content", "")

                return self._extract_score(content)

        except Exception as e:
            logger.error(f"Ollama Error: {e}", exc_info=True)
            return None

class QwenJudge(BaseJudge):
    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        if not settings.QWEN_API_KEY: return None
        dashscope.api_key = settings.QWEN_API_KEY
        try:
            # Оборачиваем синхронный вызов в asyncio.to_thread для правильной async обработки
            response = await asyncio.to_thread(
                dashscope.Generation.call,
                model=settings.QWEN_MODEL,  # Или 'qwen-plus' (поумнее)
                messages=[
                    {'role': 'user', 'content': JUDGE_PROMPT.format(code=code, doc=doc)}
                ],
                result_format='message',
            )

            if response.status_code == HTTPStatus.OK:
                content = response.output.choices[0]['message']['content']
                return self._extract_score(content)
            else:
                logger.error(f"Qwen Error: {response.code} - {response.message}")
                return None

        except Exception as e:
            logger.error(f"Qwen Connection Error: {e}", exc_info=True)
            return None