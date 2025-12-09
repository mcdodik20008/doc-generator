from pydantic_settings import BaseSettings, SettingsConfigDict
from functools import lru_cache

class Settings(BaseSettings):
    # --- App Settings ---
    API_V1_STR: str = "/api/v1"
    PROJECT_NAME: str = "Doc Evaluator Service"

    # --- LLM Settings ---
    # Значения по умолчанию стоят для локальной разработки
    OLLAMA_HOST: str = "http://ollama:11434/v1"
    OLLAMA_MODEL: str = "qwen2.5:7b"

    # --- Credentials (Optional) ---
    # Pydantic сам поймет, что если в .env пусто, то будет None
    GIGACHAT_CREDENTIALS: str | None = None
    GEMINI_API_KEY: str | None = None

    # --- Weights (Настройка баланса) ---
    WEIGHT_SEMANTIC: float = 0.2
    WEIGHT_COVERAGE: float = 0.2
    WEIGHT_LLM: float = 0.6
    
    # --- Advanced Settings ---
    SELF_CONSISTENCY_ROUNDS: int = 3

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