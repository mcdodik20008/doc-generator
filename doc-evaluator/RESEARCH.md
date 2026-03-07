# Исследование: Замена CodeBERT + Русский Readability

## Часть 1: Замена CodeBERT на модель для code-doc similarity

### Проблема текущего подхода

`microsoft/codebert-base` — это Masked Language Model (MLM + Replaced Token Detection) на базе RoBERTa-base.
Он **НЕ обучался для sentence-level embeddings или cosine similarity**. При загрузке через `SentenceTransformer`
применяется mean pooling поверх token embeddings, но модель никогда не тренировалась с contrastive или
sentence-embedding objective. Это означает:
- Cosine similarity между несвязанными парами код/документация может быть необоснованно высокой
- Embedding space не структурирован для similarity ranking
- Модель работает как general-purpose code LM, а не как embedding model

### Сравнительная таблица кандидатов

| Модель | Params | Disk | Embed Dim | SentenceTransformer | Обучена для Code-NL | CSN MRR | Изменения кода |
|--------|--------|------|-----------|--------------------|--------------------|---------|---------------|
| **microsoft/codebert-base** (текущая) | 125M | ~500MB | 768 | Да (но misuse) | **Нет** (MLM) | 69.3 | — |
| **krlvi/sentence-t5-base-nlpl-code_search_net** | ~110M | ~440MB | 768 | **Да (native)** | **Да** (contrastive) | N/A* | **Никаких** |
| **flax-sentence-embeddings/st-codesearch-distilroberta-base** | ~82M | ~330MB | 768 | **Да (native)** | **Да** (contrastive) | N/A* | **Никаких** |
| **Salesforce/codet5p-110m-embedding** | 110M | ~440MB | 256 | Нет | **Да** (bimodal) | 74.2 | Средние |
| **microsoft/unixcoder-base** | 125M | ~500MB | 768 | Нет | **Да** (multimodal) | 74.4 | Большие |
| **sjiang1/codecse** (CodeCSE) | ~125M | ~500MB | 768 | Нет | **Да** (contrastive) | 74.9 | Большие |

*N/A — модели не бенчмаркались на стандартном CodeSearchNet MRR, но обучены на CodeSearchNet парах с contrastive loss.

### Детальное описание кандидатов

#### 1. krlvi/sentence-t5-base-nlpl-code_search_net — ЛУЧШАЯ ЗАМЕНА (drop-in)

- **Архитектура:** T5EncoderModel + mean pooling + dense + L2 normalization
- **Обучение:** Fine-tuned от `sentence-t5-base` на CodeSearchNet с MultipleNegativesRankingLoss (contrastive)
- **Max seq length:** 256 tokens
- **Почему лучший:** Нативный SentenceTransformer, обучен именно для code-NL similarity, тот же размер что CodeBERT
- **Миграция:** Сменить `CODEBERT_MODEL_NAME` в конфиге. Ноль изменений кода.

#### 2. flax-sentence-embeddings/st-codesearch-distilroberta-base — лёгкая альтернатива

- **Архитектура:** DistilRoBERTa + mean pooling
- **Обучение:** CodeSearchNet с MultipleNegativesRankingLoss (dot product)
- **Max seq length:** 128 tokens (может обрезать длинный код)
- **Плюсы:** Меньше, быстрее. Нативный SentenceTransformer.
- **Минусы:** Только 128 токенов, авторы отмечают как "preliminary model"

#### 3. Salesforce/codet5p-110m-embedding — лучшие бенчмарки среди "средних" моделей

- **Архитектура:** CodeT5+ encoder
- **Обучение:** Bimodal contrastive + text-code matching
- **Бенчмарки CodeSearchNet (zero-shot):**

| Ruby | JS | Go | Python | Java | PHP | Overall |
|------|----|----|--------|------|-----|---------|
| 74.51 | 69.07 | 90.69 | 71.55 | 71.82 | 67.72 | 74.23 |

- **Плюсы:** Отличные бенчмарки, компактные 256-dim embeddings
- **Минусы:** НЕ совместим с SentenceTransformer — требует `AutoModel`/`AutoTokenizer` с `trust_remote_code=True`

#### 4. microsoft/unixcoder-base — максимальная точность

- **Архитектура:** RoBERTa + AST structural information
- **Обучение:** MLM, contrastive, generation (multimodal)
- **Бенчмарки CodeSearchNet (fine-tuned):**

| Ruby | JS | Go | Python | Java | PHP | Overall |
|------|----|----|--------|------|-----|---------|
| 74.0 | 68.4 | 91.5 | 72.0 | 72.6 | 67.6 | 74.4 |

- **Плюсы:** Highest MRR, учитывает AST
- **Минусы:** Требует кастомный `UniXcoder` класс из Microsoft CodeBERT repo — значительные изменения кода

#### 5. sjiang1/codecse (CodeCSE) — сильная contrastive модель

- **Архитектура:** GraphCodeBERT-based
- **Обучение:** Bi-modal contrastive learning на CodeSearchNet
- **Бенчмарки:** Overall MRR 74.9
- **Минусы:** Требует кастомный CodeCSE repository и GraphCodeBERT dependencies

#### 6. Salesforce/codet5-base — НЕ РЕКОМЕНДУЕТСЯ

- Это seq2seq модель для генерации (code summarization, code generation)
- НЕ embedding model, не обучена с similarity objective
- Использование для cosine similarity имеет ту же проблему что и CodeBERT-base

### Рекомендация

**Основная:** `krlvi/sentence-t5-base-nlpl-code_search_net` — zero code changes, обучена для задачи.
**Альтернативная:** `flax-sentence-embeddings/st-codesearch-distilroberta-base` — если нужно меньше/быстрее.
**Если нужна максимальная точность:** `Salesforce/codet5p-110m-embedding` — но требует рефакторинг.

---

## Часть 2: Русский Readability

### Библиотеки для определения языка

| Библиотека | Версия | Размер | Точность | Скорость | Поддержка | Рекомендация |
|-----------|--------|--------|----------|----------|-----------|-------------|
| **langdetect** | 1.0.9 (2021) | 981 KB | 92.47% | ~234 sent/s | Стабильная | Хороший выбор |
| **langid** | — | 36 MB RAM | 90.15% | ~1,269 sent/s | Стабильная | Быстрее, менее точная |
| **fast-langdetect** | 1.0.0 (2025) | 45-210 MB | ~95%+ | ~112K sent/s | Активная | Лучшая по скорости |
| **lingua** | 2.1.1 | <1 GB RAM | Лучшая для коротких текстов | Средняя | Активная | Overkill |

### Библиотеки для русской токенизации

| Библиотека | Размер | Зависимости | Что делает | Рекомендация |
|-----------|--------|-------------|-----------|-------------|
| **razdel** | 21 KB | Ноль | Токенизация слов, разбиение на предложения | **Идеальный выбор** |
| **natasha** | Тяжёлая | razdel, slovnet, yargy, navec | NER, морфология, синтаксис, лемматизация | Overkill для readability |

### Формулы читаемости для русского текста

#### Формула Обрневой (Flesch адаптированная для русского, 2005)

```
FRE_ru = 206.835 - 1.52 * ASL - 65.14 * ASW
```

Где:
- **ASL** = средняя длина предложения (слов на предложение)
- **ASW** = среднее число слогов на слово

Сравнение с английским оригиналом:
```
FRE_en = 206.835 - 1.015 * ASL - 84.6 * ASW
```

Различия:
- Коэффициент ASL выше (1.52 vs 1.015) — русские предложения короче (нет артиклей, меньше вспомогательных глаголов)
- Коэффициент ASW ниже (65.14 vs 84.6) — русские слова длиннее по слогам

Шкала 0-100, где выше = легче читается.

#### Coleman-Liau Index (языко-агностичный)

```
CLI = 0.0588 * L - 0.296 * S - 15.8
```

Где L = среднее букв на 100 слов, S = среднее предложений на 100 слов.
Работает для русского без модификации, т.к. использует подсчёт символов, а не слогов.

#### Подсчёт слогов в русском

В русском языке подсчёт слогов тривиален — **каждый слог содержит ровно одну гласную**.
Русские гласные: `а, е, ё, и, о, у, ы, э, ю, я`

```python
RUSSIAN_VOWELS = set('аеёиоуыэюяАЕЁИОУЫЭЮЯ')

def count_russian_syllables(word: str) -> int:
    return max(1, sum(1 for ch in word if ch in RUSSIAN_VOWELS))
```

Это 100% точно (в отличие от английского, где подсчёт слогов — сложная задача, требующая словарей).

### Важное открытие: textstat >= 0.7.13 поддерживает русский

`textstat` начиная с версии 0.7.13 (Feb 2026) поддерживает русский через `set_lang("ru")`.
Используется **pyphen** с словарём `hyph_ru_RU.dic` (LibreOffice Russian hyphenation dictionary).
Однако формула может использовать стандартные английские коэффициенты Flesch, а не адаптированные Обрневой.

### Рекомендация

**Option B: Кастомная реализация с razdel + формула Обрневой**

1. **razdel** (21 KB, zero deps) для русской токенизации
2. **langdetect** (981 KB) для определения языка
3. Кастомная формула Обрневой для русского
4. Оставить **textstat** для английского

---

## Источники

### CodeBERT / Code Embeddings
- [microsoft/codebert-base — HuggingFace](https://huggingface.co/microsoft/codebert-base)
- [microsoft/unixcoder-base — HuggingFace](https://huggingface.co/microsoft/unixcoder-base)
- [Salesforce/codet5p-110m-embedding — HuggingFace](https://huggingface.co/Salesforce/codet5p-110m-embedding)
- [krlvi/sentence-t5-base-nlpl-code_search_net — HuggingFace](https://huggingface.co/krlvi/sentence-t5-base-nlpl-code_search_net)
- [flax-sentence-embeddings/st-codesearch-distilroberta-base — HuggingFace](https://huggingface.co/flax-sentence-embeddings/st-codesearch-distilroberta-base)
- [CodeCSE paper (arXiv:2407.06360)](https://arxiv.org/html/2407.06360v1)
- [UniXcoder paper (arXiv:2203.03850)](https://ar5iv.labs.arxiv.org/html/2203.03850)
- [CodeT5+ paper (arXiv:2305.07922)](https://arxiv.org/pdf/2305.07922)
- [Sentence Transformers Documentation](https://sbert.net/)
- [CodeSearchNet GitHub Repository](https://github.com/github/CodeSearchNet)
- [Microsoft CodeBERT GitHub Repository](https://github.com/microsoft/CodeBERT)
- [Zilliz — Embedding Models for Code](https://zilliz.com/ai-faq/what-embedding-models-work-best-for-code-and-technical-content)

### Russian Readability
- [Flesch formula for Russian (Обрнева) — Orfogrammka](https://orfogrammka.ru/%D1%88%D0%BF%D0%B0%D1%80%D0%B3%D0%B0%D0%BB%D0%BA%D0%B8/%D0%B8%D0%BD%D0%B4%D0%B5%D0%BA%D1%81_%D1%83%D0%B4%D0%BE%D0%B1%D0%BE%D1%87%D0%B8%D1%82%D0%B0%D0%B5%D0%BC%D0%BE%D1%81%D1%82%D0%B8_%D1%84%D0%BB%D0%B5%D1%88%D0%B0/)
- [Readability Formula for Russian Texts (MICAI 2018)](https://www.researchgate.net/publication/330086747)
- [Readability Formulas for Russian School Textbooks (Springer)](https://link.springer.com/article/10.1007/s10958-024-07436-y)
- [Coleman-Liau Index (Wikipedia)](https://en.wikipedia.org/wiki/Coleman%E2%80%93Liau_index)
- [Flesch-Kincaid readability tests (Wikipedia)](https://en.wikipedia.org/wiki/Flesch%E2%80%93Kincaid_readability_tests)

### Python Libraries
- [razdel — PyPI](https://pypi.org/project/razdel/) | [GitHub](https://github.com/natasha/razdel)
- [natasha — PyPI](https://pypi.org/project/natasha/)
- [textstat — PyPI](https://pypi.org/project/textstat/) | [GitHub](https://github.com/textstat/textstat)
- [langdetect — PyPI](https://pypi.org/project/langdetect/)
- [fast-langdetect — PyPI](https://pypi.org/project/fast-langdetect/) | [GitHub](https://github.com/LlmKira/fast-langdetect)
- [lingua-language-detector — PyPI](https://pypi.org/project/lingua-language-detector/) | [GitHub](https://github.com/pemistahl/lingua-py)
- [Language identification benchmark survey (ModelPredict)](https://modelpredict.com/language-identification-survey)
- [pyphen Russian dictionary (hyph_ru_RU.dic) — GitHub](https://github.com/Kozea/Pyphen/blob/master/pyphen/dictionaries/hyph_ru_RU.dic)
