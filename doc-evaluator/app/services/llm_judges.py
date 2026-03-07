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

logger = logging.getLogger(__name__)

JUDGE_PROMPT = """
Ты — Senior Technical Writer с 10-летним опытом.
Оцени качество документации к данному коду.

Код:
```
{code}
```

Документация:
```
{doc}
```

Критерии оценки:
1. Полнота — описаны ли все параметры, возвращаемое значение, исключения, побочные эффекты?
2. Точность — соответствует ли документация реальному поведению кода?
3. Ясность — понятна ли документация разработчику без изучения кода?
4. Структура — есть ли форматирование, разделы, примеры использования?

Шкала:
0-2: документация отсутствует или полностью неверна
3-4: минимальная документация, много пропусков
5-6: средняя документация, покрыты основные аспекты
7-8: хорошая документация, мелкие недочёты
9-10: отличная документация, полная и точная

Верни ТОЛЬКО одно число от 0 до 10 (допускается дробное, например 7.5).
"""

# Максимальное количество попыток при transient-ошибках
MAX_RETRIES = 2
RETRY_DELAY_SECONDS = 1.0


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
            logger.warning("Failed to extract score from text: %s", e)
        return None

    async def _retry_evaluate(self, func, retries: int = MAX_RETRIES):
        """Обёртка с retry и exponential backoff для transient-ошибок"""
        last_error = None
        for attempt in range(retries + 1):
            try:
                return await func()
            except (asyncio.TimeoutError, ConnectionError, OSError) as e:
                last_error = e
                if attempt < retries:
                    delay = RETRY_DELAY_SECONDS * (2 ** attempt)
                    logger.warning(
                        "Retry %d/%d after error: %s (waiting %.1fs)",
                        attempt + 1, retries, e, delay
                    )
                    await asyncio.sleep(delay)
        logger.error("All %d retries exhausted. Last error: %s", retries, last_error)
        return None


class GigaChatJudge(BaseJudge):
    def __init__(self):
        self._settings = get_settings()

    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        if not self._settings.GIGACHAT_CREDENTIALS:
            return None

        async def _call():
            from gigachat.models import Chat, Messages, MessagesRole

            async with GigaChat(
                credentials=self._settings.GIGACHAT_CREDENTIALS,
                verify_ssl_certs=self._settings.GIGACHAT_VERIFY_SSL
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

        try:
            return await self._retry_evaluate(_call)
        except Exception as e:
            logger.error("GigaChat Error: %s", e, exc_info=True)
            return None


class GeminiJudge(BaseJudge):
    def __init__(self):
        self._settings = get_settings()
        if self._settings.GEMINI_API_KEY:
            genai.configure(api_key=self._settings.GEMINI_API_KEY)

    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        if not self._settings.GEMINI_API_KEY:
            return None

        async def _call():
            model = genai.GenerativeModel(self._settings.GEMINI_MODEL)
            response = await asyncio.to_thread(
                model.generate_content,
                JUDGE_PROMPT.format(code=code, doc=doc),
                generation_config=genai.types.GenerationConfig(temperature=temperature)
            )
            return self._extract_score(response.text)

        try:
            return await self._retry_evaluate(_call)
        except Exception as e:
            logger.error("Gemini Error: %s", e, exc_info=True)
            return None


class OllamaJudge(BaseJudge):
    REQUEST_TIMEOUT = 30  # секунд

    def __init__(self):
        self._settings = get_settings()
        timeout = aiohttp.ClientTimeout(total=self.REQUEST_TIMEOUT)
        self._session = aiohttp.ClientSession(timeout=timeout)

    async def close(self):
        """Закрывает ClientSession. Вызывается при shutdown приложения."""
        if self._session and not self._session.closed:
            await self._session.close()

    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        if not code or not doc:
            logger.warning("Empty code or doc in OllamaJudge.evaluate")
            return None

        base_url = self._settings.OLLAMA_HOST.rstrip('/').replace('/v1', '')
        url = f"{base_url}/api/chat"

        payload = {
            "model": self._settings.OLLAMA_MODEL,
            "messages": [
                {"role": "user", "content": JUDGE_PROMPT.format(code=code, doc=doc)}
            ],
            "stream": False,
            "options": {
                "temperature": temperature
            }
        }

        async def _call():
            async with self._session.post(url, json=payload) as response:
                if response.status != 200:
                    error_text = await response.text()
                    logger.error("Ollama Error %d: %s", response.status, error_text)
                    return None
                result = await response.json()
                content = result.get("message", {}).get("content", "")
                return self._extract_score(content)

        try:
            return await self._retry_evaluate(_call)
        except Exception as e:
            logger.error("Ollama Error: %s", e, exc_info=True)
            return None


class QwenJudge(BaseJudge):
    def __init__(self):
        self._settings = get_settings()

    async def evaluate(self, code: str, doc: str, temperature: float = 0.1) -> float | None:
        if not self._settings.QWEN_API_KEY:
            return None
        dashscope.api_key = self._settings.QWEN_API_KEY

        async def _call():
            response = await asyncio.to_thread(
                dashscope.Generation.call,
                model=self._settings.QWEN_MODEL,
                messages=[
                    {'role': 'user', 'content': JUDGE_PROMPT.format(code=code, doc=doc)}
                ],
                result_format='message',
            )
            if response.status_code == HTTPStatus.OK:
                content = response.output.choices[0]['message']['content']
                return self._extract_score(content)
            else:
                logger.error("Qwen Error: %s - %s", response.code, response.message)
                return None

        try:
            return await self._retry_evaluate(_call)
        except Exception as e:
            logger.error("Qwen Connection Error: %s", e, exc_info=True)
            return None
