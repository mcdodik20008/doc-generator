FROM python:3.12-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .

# Ставим CPU торч
# Увеличиваем таймаут для скачивания моделей (было 10 сек по умолчанию)
ENV HF_HUB_DOWNLOAD_TIMEOUT=100

RUN pip install --no-cache-dir torch>=2.2.0 --index-url https://download.pytorch.org/whl/cpu
RUN pip install --no-cache-dir -r requirements.txt

# Копируем всю папку app
COPY app ./app

# Мы просто дернем сервис один раз, чтобы он скачал веса
RUN python -c "from app.services.local_metrics import LocalMetricsService; LocalMetricsService.get_instance()"

EXPOSE 8000

# Запускаем через модуль
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]