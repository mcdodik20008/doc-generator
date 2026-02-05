from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import field_validator
from functools import lru_cache

class Settings(BaseSettings):
    # --- App Settings ---
    API_V1_STR: str = "/api/v1"
    PROJECT_NAME: str = "Doc Evaluator Service"

    # --- LLM Settings ---
    # Значения по умолчанию стоят для локальной разработки
    OLLAMA_HOST: str = "http://localhost:11434/v1"
    # TODO: Модель жестко задана - рассмотреть возможность поддержки нескольких моделей
    OLLAMA_MODEL: str = "qwen2.5:7b"

    @field_validator('OLLAMA_HOST')
    @classmethod
    def validate_ollama_host(cls, v: str) -> str:
        """Проверяет что OLLAMA_HOST является валидным HTTP/HTTPS URL"""
        v = v.strip()
        if not v:
            raise ValueError('OLLAMA_HOST cannot be empty')
        if not (v.startswith('http://') or v.startswith('https://')):
            raise ValueError(
                f'OLLAMA_HOST must start with http:// or https://, got: {v}'
            )
        return v

    # --- Credentials (Optional) ---
    # Pydantic сам поймет, что если в .env пусто, то будет None
    GIGACHAT_CREDENTIALS: str | None = None
    GIGACHAT_VERIFY_SSL: bool = True  # Добавлено для контроля проверки SSL
    GEMINI_API_KEY: str | None = None
    QWEN_API_KEY: str | None = None  # Добавлено для QwenJudge
    QWEN_MODEL: str = "qwen-turbo"  # Модель по умолчанию для Qwen

    # --- Local Metrics Settings ---
    # Модель для вычисления семантического сходства кода и документации
    CODEBERT_MODEL_NAME: str = "microsoft/codebert-base"

    # --- Weights (Настройка баланса) ---
    WEIGHT_SEMANTIC: float = 0.2
    WEIGHT_COVERAGE: float = 0.2
    WEIGHT_LLM: float = 0.6

    @field_validator('WEIGHT_SEMANTIC', 'WEIGHT_COVERAGE', 'WEIGHT_LLM')
    @classmethod
    def validate_weight_non_negative(cls, v: float, info) -> float:
        """Проверяет что вес неотрицательный"""
        if v < 0:
            raise ValueError(f'{info.field_name} must be non-negative, got {v}')
        return v

    def model_post_init(self, __context) -> None:
        """Проверяет что сумма всех весов = 1.0"""
        total = self.WEIGHT_SEMANTIC + self.WEIGHT_COVERAGE + self.WEIGHT_LLM
        if not (0.99 <= total <= 1.01):  # Допускаем небольшую погрешность
            raise ValueError(
                f'Sum of weights must equal 1.0, got {total:.4f} '
                f'(SEMANTIC={self.WEIGHT_SEMANTIC}, COVERAGE={self.WEIGHT_COVERAGE}, LLM={self.WEIGHT_LLM})'
            )

    # --- Advanced Settings ---
    SELF_CONSISTENCY_ROUNDS: int = 1

    @field_validator('SELF_CONSISTENCY_ROUNDS')
    @classmethod
    def validate_consistency_rounds(cls, v: int) -> int:
        """Проверяет что количество раундов в разумных пределах"""
        if v < 1:
            raise ValueError(f'SELF_CONSISTENCY_ROUNDS must be >= 1, got {v}')
        if v > 10:
            raise ValueError(
                f'SELF_CONSISTENCY_ROUNDS must be <= 10 to prevent system overload, got {v}. '
                f'Higher values significantly increase API costs and latency.'
            )
        return v

    # ВАЖНЫЙ МОМЕНТ:
    # 1. Pydantic сначала читает системные переменные (Environment Variables).
    # 2. Если их нет, он ищет файлы из списка env_file.
    # 3. Мы указываем искать и в текущей папке, и в папке выше (для локального запуска).
    model_config = SettingsConfigDict(
        env_file=[".env", "../.env"],
        env_file_encoding="utf-8",
        extra="ignore"
    )

@lru_cache
def get_settings():
    return Settings()