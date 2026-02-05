"""
DocEvaluatorClient - клиент для взаимодействия с API сервиса оценки документации.

Этот модуль предоставляет асинхронный клиент для отправки запросов на оценку
качества документации к сервису doc-evaluator.

Пример использования:
    async with DocEvaluatorClient() as client:
        result = await client.evaluate(
            code_snippet="def add(a, b): return a + b",
            generated_doc="Function adds two numbers"
        )
        print(f"Score: {result.final_score}")
"""
import logging
from typing import Optional
import aiohttp
from pydantic import BaseModel, Field, ValidationError as PydanticValidationError


# ===== MODELS =====

class LlmScores(BaseModel):
    """Оценки от LLM судей."""
    gigachat: Optional[float] = None
    gemini: Optional[float] = None
    ollama: Optional[float] = None


class EvaluationResult(BaseModel):
    """Результат оценки документации."""
    # Локальные метрики
    semantic_score: float = Field(..., ge=0, le=10, description="Семантическое сходство")
    keyword_coverage: float = Field(..., ge=0, le=10, description="Покрытие ключевыми словами")
    readability_score: float = Field(..., description="Читаемость текста")
    
    # LLM метрики
    llm_scores: LlmScores
    
    # Итоговые метрики
    final_score: float = Field(..., ge=0, le=10, description="Итоговая взвешенная оценка")
    score_variance: float = Field(..., ge=0, description="Дисперсия оценок")
    confidence_score: float = Field(..., ge=0, le=1, description="Уверенность в оценке")


# ===== EXCEPTIONS =====

class DocEvaluatorError(Exception):
    """Базовое исключение для ошибок клиента."""
    pass


class ServiceUnavailableError(DocEvaluatorError):
    """Сервис недоступен."""
    pass


class ValidationError(DocEvaluatorError):
    """Ошибка валидации входных данных."""
    pass


class APIError(DocEvaluatorError):
    """Общая ошибка API."""
    pass


# ===== CLIENT =====

class DocEvaluatorClient:
    """
    Асинхронный клиент для сервиса оценки документации.
    
    Attributes:
        base_url: Базовый URL сервиса (по умолчанию http://localhost:8000)
        timeout: Таймаут запросов в секундах (по умолчанию 30)
        log_level: Уровень логирования
    
    Example:
        >>> async with DocEvaluatorClient() as client:
        ...     result = await client.evaluate("code", "doc")
        ...     print(result.final_score)
    """
    
    def __init__(
        self,
        base_url: str = "http://localhost:8000",
        timeout: int = 30,
        log_level: int = logging.WARNING
    ):
        """
        Инициализирует клиент.

        Args:
            base_url: URL сервиса doc-evaluator
            timeout: Таймаут для HTTP запросов в секундах
            log_level: Уровень логирования (logging.DEBUG, logging.INFO, etc.)
        """
        # TODO: Нет валидации base_url (может быть невалидный URL)
        self.base_url = base_url.rstrip('/')
        # TODO: Нет настройки отдельных таймаутов (connect, read, write) - только total
        self.timeout = aiohttp.ClientTimeout(total=timeout)
        self._session: Optional[aiohttp.ClientSession] = None
        
        # Настройка логирования
        self.logger = logging.getLogger(self.__class__.__name__)
        self.logger.setLevel(log_level)
        if not self.logger.handlers:
            handler = logging.StreamHandler()
            handler.setFormatter(
                logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
            )
            self.logger.addHandler(handler)
    
    async def __aenter__(self):
        """Вход в context manager - создаёт сессию."""
        await self._ensure_session()
        return self
    
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        """Выход из context manager - закрывает сессию."""
        await self.close()
    
    async def _ensure_session(self):
        """Создаёт сессию если её ещё нет."""
        if self._session is None or self._session.closed:
            self._session = aiohttp.ClientSession(timeout=self.timeout)
            self.logger.debug(f"Created new session for {self.base_url}")
    
    async def close(self):
        """Закрывает HTTP сессию."""
        if self._session and not self._session.closed:
            await self._session.close()
            self.logger.debug("Session closed")
    
    async def health_check(self) -> dict:
        """
        Проверяет доступность сервиса.
        
        Returns:
            Словарь со статусом сервиса
            
        Raises:
            ServiceUnavailableError: Если сервис недоступен
        """
        await self._ensure_session()
        url = f"{self.base_url}/health"
        
        try:
            self.logger.debug(f"Health check: {url}")
            async with self._session.get(url) as response:
                if response.status == 200:
                    data = await response.json()
                    self.logger.info(f"Service is healthy: {data}")
                    return data
                else:
                    raise ServiceUnavailableError(
                        f"Health check failed with status {response.status}"
                    )
        except aiohttp.ClientConnectionError as e:
            self.logger.error(f"Connection error: {e}")
            raise ServiceUnavailableError(
                f"Cannot connect to service at {self.base_url}"
            ) from e
        except aiohttp.ClientError as e:
            self.logger.error(f"Client error: {e}")
            raise ServiceUnavailableError(str(e)) from e
    
    async def evaluate(
        self,
        code_snippet: str,
        generated_doc: str
    ) -> EvaluationResult:
        """
        Оценивает качество документации для заданного кода.

        Args:
            code_snippet: Исходный код (минимум 5 символов)
            generated_doc: Сгенерированная документация (минимум 5 символов)

        Returns:
            Результат оценки с метриками и итоговым скором

        Raises:
            ValidationError: Если входные данные невалидны
            ServiceUnavailableError: Если сервис недоступен
            APIError: При других ошибках API
        """
        # TODO: Отсутствует retry логика для временных сетевых ошибок
        # TODO: Отсутствует rate limiting (можно перегрузить сервер запросами)
        # TODO: Нет кеширования результатов для одинаковых запросов
        await self._ensure_session()

        # Валидация входных данных
        # TODO: Магическое число 5 - вынести в константу MIN_INPUT_LENGTH
        # TODO: Нет проверки максимальной длины (можно передать гигабайты текста)
        if len(code_snippet) < 5:
            raise ValidationError("Code snippet must be at least 5 characters long")
        if len(generated_doc) < 5:
            raise ValidationError("Generated documentation must be at least 5 characters long")
        
        url = f"{self.base_url}/evaluate"
        payload = {
            "code_snippet": code_snippet,
            "generated_doc": generated_doc
        }
        
        try:
            self.logger.debug(f"Sending evaluation request to {url}")
            self.logger.debug(f"Code length: {len(code_snippet)}, Doc length: {len(generated_doc)}")
            
            async with self._session.post(url, json=payload) as response:
                response_text = await response.text()
                
                if response.status == 200:
                    try:
                        data = await response.json()
                        result = EvaluationResult(**data)
                        self.logger.info(f"Evaluation successful: score={result.final_score:.2f}")
                        return result
                    except PydanticValidationError as e:
                        self.logger.error(f"Response validation error: {e}")
                        raise APIError(f"Invalid response format: {e}") from e
                
                elif response.status == 422:
                    # Ошибка валидации от FastAPI
                    try:
                        error_data = await response.json()
                        detail = error_data.get('detail', 'Validation error')
                        raise ValidationError(f"API validation error: {detail}")
                    except Exception:
                        raise ValidationError(f"Validation error: {response_text}")
                
                elif response.status == 503:
                    raise ServiceUnavailableError("Service is temporarily unavailable")
                
                else:
                    self.logger.error(f"API error {response.status}: {response_text}")
                    # TODO: Обрезка response_text до 200 символов может потерять важную информацию об ошибке
                    # TODO: Добавить полный текст ошибки в логи, а в исключение - сокращенный
                    raise APIError(
                        f"API returned status {response.status}: {response_text[:200]}"
                    )
        
        except aiohttp.ClientConnectionError as e:
            self.logger.error(f"Connection error: {e}")
            raise ServiceUnavailableError(
                f"Cannot connect to service at {self.base_url}"
            ) from e
        
        except (ValidationError, ServiceUnavailableError, APIError):
            # Пробрасываем наши собственные исключения без изменений
            raise
        
        except Exception as e:
            self.logger.exception("Unexpected error during evaluation")
            raise APIError(f"Unexpected error: {str(e)}") from e


# ===== CONVENIENCE FUNCTION =====

async def evaluate_doc(
    code_snippet: str,
    generated_doc: str,
    base_url: str = "http://localhost:8000",
    timeout: int = 30
) -> EvaluationResult:
    """
    Удобная функция для разовой оценки документации.
    
    Автоматически создаёт и закрывает клиент.
    
    Args:
        code_snippet: Исходный код
        generated_doc: Документация
        base_url: URL сервиса
        timeout: Таймаут в секундах
        
    Returns:
        Результат оценки
        
    Example:
        >>> result = await evaluate_doc("def foo(): pass", "Function foo")
        >>> print(result.final_score)
    """
    async with DocEvaluatorClient(base_url=base_url, timeout=timeout) as client:
        return await client.evaluate(code_snippet, generated_doc)
