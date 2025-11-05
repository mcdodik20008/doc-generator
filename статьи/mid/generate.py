# Create LaTeX project files for arXiv preprint (EN + RU)
import os, textwrap, json, pathlib

base = "./"
os.makedirs(base, exist_ok=True)
os.makedirs(f"{base}/images", exist_ok=True)

readme = """# arXiv Preprint (EN + RU)

This archive contains two LaTeX sources:

- `main_en.tex` — English version (recommended for arXiv)
- `main_ru.tex` — Russian version

## How to compile (locally)
- Install TeX Live / MiKTeX
- Run:
  - `pdflatex main_en.tex` (twice) OR `latexmk -pdf main_en.tex`
  - `pdflatex main_ru.tex` (twice) OR `latexmk -pdf main_ru.tex`

## Notes
- We use the standard `article` class to ensure compatibility with arXiv.
- You can later convert the manuscript to MDPI style by moving content into MDPI's official template.
"""

open(f"{base}/README.md", "w", encoding="utf-8").write(readme)

main_ru = r"""\
\documentclass[11pt,a4paper]{article}
\usepackage[utf8]{inputenc}
\usepackage[russian,english]{babel}
\usepackage{geometry}
\geometry{margin=1in}
\usepackage{hyperref}
\usepackage{csquotes}
\usepackage{enumitem}

\title{Автоматизация документации кода с использованием LLM и графовых моделей системы: метаанализ подходов и архитектура решения}
\author{Alex Kaspshitskii\\
Tyumen State University, Tyumen, Russia\\
\texttt{mcdodik2008@gmail.com}}
\date{\today}

\begin{document}
\selectlanguage{russian}
\maketitle

\begin{abstract}
Статья рассматривает проблему разрыва между исходным кодом и документацией в современных программных проектах и анализирует пять основных направлений автоматизации документирования: суммаризацию кода, LLM-инструменты в IDE, автоматическую генерацию API-документации, Retrieval-Augmented Generation (RAG) над кодовой базой и графовые подходы (Code Graph + LLM). Показывается, что первые четыре направления решают локальные задачи и не обеспечивают системного охвата, тогда как интеграция LLM с графовыми моделями системы позволяет масштабировать контекст до уровня архитектуры и поддерживать актуальность документации. Предлагается контур решения: \emph{GIT $\rightarrow$ Graph Builder $\rightarrow$ Code Graph $\rightarrow$ Chunks $\rightarrow$ RAG $\rightarrow$ Local LLM $\rightarrow$ Диалог с системным контекстом}.
\end{abstract}

\textbf{Ключевые слова:} LLM; документация кода; RAG; граф кода; граф знаний; программная инженерия.

\section{Введение}
Ключевая проблема современной системной разработки --- расхождение между кодом и документацией. Темпы внесения изменений в программный продукт настолько высоки, что документирование требований физически не успевает за ними. В результате, технические задания становятся неактуальными практически в момент их написания. Причиной тому служат не только хотфиксы, но и реализация устных договорённостей, которые идут вразрез с исходной постановкой. Эти «теневые изменения», изначально воспринимаемые как временные, быстро становятся нормой. Усиливается это постоянным давлением бизнеса и гонкой за time-to-market.

В результате документация перестаёт быть зеркалом системы: она фрагментарна, неактуальна и зачастую воспринимается разработчиками как формальность. Это приводит к накоплению технического долга, усложняет внедрение в процесс разработки новых специалистов, снижает прозрачность архитектуры сервиса и системы в целом, повышает риск ошибок при доработке или модернизации системы.

Проблема признана не только в индустрии, но и в академическом сообществе. Исследования последних лет отмечают систематический разрыв между исходным кодом, архитектурными решениями и документацией~\cite{zhang2022inconsistent, li2023coev}. Отдельные работы указывают, что значительная часть проектной документации (до 60--70\%) теряет актуальность спустя всего несколько месяцев после начала разработки, если её поддержка не автоматизирована~\cite{forward2002relevance, storey2021future}.

На этом фоне растёт интерес к использованию больших языковых моделей (LLM) для автоматизированной генерации, обновления и анализа документации по коду. Уже существуют работы по генерации комментариев и docstring на основе исходного кода (например, CodeBERT~\cite{feng2020codebert} и CodeT5~\cite{wang2021codet5}), моделям диалогового взаимодействия с репозиторием (RAG over codebase~\cite{siddiq2024rag}, GitHub Copilot Chat, Sourcegraph Cody) и построению графовой структуры с последующим объяснением архитектуры с помощью LLM~\cite{peng2024structural}. Остаётся открытым вопрос: можно ли создать систему, в которой документация не «догоняет» код, а рождается вместе с ним и остаётся актуальной автоматически?

\section{Краткий обзор существующих подходов}

\subsection{Code summarization --- генерация кратких описаний из кода}
Подход фокусируется на моделях (CodeBERT~\cite{feng2020codebert}, CodeT5~\cite{wang2021codet5}, GraphCodeBERT~\cite{guo2021graphcodebert}), которые обучаются генерировать краткие текстовые описания (docstrings, комментарии) на основе анализа исходного кода и его синтаксического представления (AST).
\begin{itemize}[nosep]
\item \textbf{Сильная сторона:} хорошие результаты на уровне функции/класса.
\item \textbf{Слабая сторона:} отсутствие системного контекста; вызовы в другие модули остаются «чёрным ящиком».
\end{itemize}

\subsection{LLM в IDE (IDE-plugins \& AI copilots)}
Интеграция LLM непосредственно в IDE (GitHub Copilot, TabNine, JetBrains AI Assistant).
\begin{itemize}[nosep]
\item \textbf{Сильная сторона:} более широкий локальный контекст (соседние файлы, иногда история коммитов)~\cite{nguyen2022copilot}.
\item \textbf{Слабые стороны:} риски конфиденциальности/NDA~\cite{vaithilingam2022usability,ziegler2024llm}, платные API, ограничение контекстного окна~\cite{liu2023lost}.
\end{itemize}

\subsection{Автоматическая API-документация (Swagger/OpenAPI + AI)}
Инструменты парсят исходники/аннотации и формируют спецификацию OpenAPI.
\begin{itemize}[nosep]
\item \textbf{Сильная сторона:} автоматическая документация внешнего интерфейса, генерация описаний~\cite{lyu2024openapi}.
\item \textbf{Слабая сторона:} не описывает внутреннюю бизнес-логику и межсервисные сценарии; спецификации часто неполны~\cite{westhofen2022openapi}.
\end{itemize}

\subsection{Retrieval-Augmented Generation (RAG) над кодовой базой}
Стандарт для Q\&A по репозиторию~\cite{siddiq2024rag}.
\begin{enumerate}[nosep]
\item Поиск релевантных фрагментов кода/документов.
\item Формирование промпта с найденным контекстом.
\item Генерация ответа LLM.
\end{enumerate}
\noindent
\textbf{Сильная сторона:} интерактивная документация на основе реального кода.\\
\textbf{Слабые стороны:} качество ретривера~\cite{shrivastava2024repoRAG}; ограничения контекста и феномен «потери в середине»~\cite{liu2023lost}.

\subsection{Code Graph + LLM --- графовая репрезентация системы}
Код представляется как граф знаний: узлы (классы, методы, модули, эндпоинты), рёбра (вызовы, наследование, зависимости, события, транзакции). LLM получает структурированную выборку связанных узлов/рёбер~\cite{peng2024structural}.
\begin{itemize}[nosep]
\item \textbf{Сильная сторона:} релевантный, причинно-следственный контекст на архитектурном уровне.
\item \textbf{Слабая сторона:} сложность построения/синхронизации графа с репозиторием.
\end{itemize}

\section{Плюсы и минусы подходов (сводная оценка)}
\begin{center}
\begin{tabular}{|p{3.8cm}|p{3cm}|p{3.2cm}|p{3.5cm}|p{2.7cm}|p{2.7cm}|p{2.7cm}|}
\hline
\textbf{Подход} & \textbf{Качество} & \textbf{Контекст сервиса} & \textbf{Контекст всей системы} & \textbf{Сложность внедрения} & \textbf{Стоимость} & \textbf{Конфиденциальность} \\ \hline
Code summarization (CodeBERT, CodeT5) & Локальные описания (функции/классы) & Да (в пределах файла/модуля) & Нет & Низкая & Низкая & Высокая (офлайн) \\ \hline
LLM в IDE (Copilot, JetBrains AI) & Выше среднего (учёт соседних файлов) & Да (структура проекта) & Частично & Низкая--Средняя & Средняя (подписки, API) & Средняя--Низкая (облако) \\ \hline
Swagger/OpenAPI + AI & Хорошо документирует интерфейс & Частично (контроллеры/DTO) & Нет & Средняя & Низкая & Высокая (локально) \\ \hline
RAG над кодовой базой & Высокое при корректном ретривале & Да (находит модуль/класс) & Частично (упирается в окно) & Средняя & Средняя--Высокая & Средняя \\ \hline
Граф кода + LLM & Наивысшее (архитектурные связи) & Полностью & Полностью & Высокая & Средняя--Высокая & Высокая (полностью локально) \\ \hline
\end{tabular}
\end{center}

\paragraph{Примечания.} Оценки опираются на~\cite{feng2020codebert,wang2021codet5,guo2021graphcodebert,nguyen2022copilot,vaithilingam2022usability,ziegler2024llm,lyu2024openapi,westhofen2022openapi,siddiq2024rag,shrivastava2024repoRAG,liu2023lost,peng2024structural}.

\section{Выводы и направление решения}
Современные LLM объясняют код на уровне отдельных сущностей, но не охватывают систему целиком; постфактум-генерация документации не решает проблему устаревания. Перспективно связать код, архитектурные связи и LLM в единый граф знаний: узлы --- классы, методы, модули, API; рёбра --- вызовы, зависимости, наследование. Поверх графа настраивается RAG, а генерация выполняется по уровням (метод $\rightarrow$ класс $\rightarrow$ сервис $\rightarrow$ бизнес-процесс). Это даёт: (i) актуальные объяснения состояния системы; (ii) автоматическую актуализацию документации; (iii) возможность строить документацию снизу вверх и сверху вниз.

Далее мы реализуем и эмпирически проверим прототип по контуру: \emph{GIT $\rightarrow$ Graph Builder $\rightarrow$ Code Graph $\rightarrow$ Chunks $\rightarrow$ RAG $\rightarrow$ Local LLM $\rightarrow$ Диалог}. Ключевые инженерные задачи: \textit{актуальность} (инкрементальная синхронизация графа с Git), \textit{навигация} (подача в LLM семантически связанных подграфов), \textit{доверие} (метрики полноты/достоверности архитектурных ответов).

\begin{thebibliography}{99}
\bibitem{zhang2022inconsistent} Zhang H., He Z., Liu Y., et al. An empirical study on the characteristics of inconsistent code comments. \textit{Empirical Software Engineering}. 2022;27:137.
\bibitem{li2023coev} Li Y., Wang S., Xia X., et al. How do developers update documentation? An empirical study on code-documentation co-evolution. \textit{Empirical Software Engineering}. 2023;28(10).
\bibitem{forward2002relevance} Forward A., Lethbridge T.C. The relevance of software documentation, tools and technologies: a survey. In: \textit{Proc. DocEng'02}. 2002. p. 26--33.
\bibitem{storey2021future} Storey M.-A. The future of software engineering. \textit{IEEE Software}. 2021;38(3):9--13.
\bibitem{feng2020codebert} Feng Z., Guo D., Tang D., et al. CodeBERT: A Pre-Trained Model for Programming and Natural Languages. \textit{Findings of ACL: EMNLP}. 2020.
\bibitem{wang2021codet5} Wang Y., Wang W., Jiao Y., et al. CodeT5: Unifying Code Understanding and Generation. In: \textit{EMNLP}. 2021.
\bibitem{siddiq2024rag} Siddiq M.L., et al. Retrieval-augmented generation for code-related tasks: A systematic review. arXiv:2404.05452, 2024.
\bibitem{peng2024structural} Peng H., et al. Augmenting code-language models with structural code knowledge: A review. arXiv:2402.13840, 2024.
\bibitem{guo2021graphcodebert} Guo D., Ren S., Lu S., et al. GraphCodeBERT: Pre-training Code Representations with Data Flow. In: \textit{ICLR}. 2021.
\bibitem{nguyen2022copilot} Nguyen N.M., Nadi S. An Empirical Evaluation of GitHub Copilot's Code Suggestions. In: \textit{MSR'22}. 2022.
\bibitem{vaithilingam2022usability} Vaithilingam P., Zhang T., Glassman E.L. Expectation vs. Experience: Evaluating the Usability of Code Generation Tools Powered by LLMs. In: \textit{CHI'22}. 2022.
\bibitem{ziegler2024llm} Ziegler J., et al. Large Language Models, Explained: A Survey of the Gaps, Limitations, and Ethics. arXiv:2407.16331, 2024.
\bibitem{lyu2024openapi} Lyu C., et al. Generating OpenAPI Specifications from API Documentation with LLMs. arXiv:2407.05316, 2024.
\bibitem{westhofen2022openapi} Westhofen M., et al. Evaluating the Quality of OpenAPI Specifications. In: \textit{IEEE ICWS}. 2022.
\bibitem{shrivastava2024repoRAG} Shrivastava A., et al. Repo-RAG: Enhancing Retrieval-Augmented Generation for Code Repositories. arXiv:2405.13115, 2024.
\bibitem{liu2023lost} Liu N.F., et al. Lost in the Middle: How Language Models Use Long Contexts. In: \textit{EMNLP}. 2023.
\bibitem{xu2024ckg} Xu B., Ma W., et al. A Code Knowledge Graph-Enhanced System for LLM-Based Fuzz Driver Generation. arXiv:2411.11532, 2024.
\end{thebibliography}

\end{document}
"""
open(f"{base}/main_ru.tex", "w", encoding="utf-8").write(main_ru)

main_en = r"""\
\documentclass[11pt,a4paper]{article}
\usepackage[utf8]{inputenc}
\usepackage[english]{babel}
\usepackage{geometry}
\geometry{margin=1in}
\usepackage{hyperref}
\usepackage{csquotes}
\usepackage{enumitem}

\title{LLM-Driven Code Documentation with System Graphs: A Meta-Analysis and Architecture Proposal}
\author{Alex Kaspshitskii\\
Tyumen State University, Tyumen, Russia\\
\texttt{mcdodik2008@gmail.com}}
\date{\today}

\begin{document}
\maketitle

\begin{abstract}
We study the persistent gap between source code and documentation in modern software projects and review five automation directions: code summarization, IDE-based LLM assistants, automatic API documentation, retrieval-augmented generation (RAG) over repositories, and graph-based approaches (Code Graph + LLM). We argue that the first four are effective for local tasks yet fail to capture system-level behavior and evolution. Integrating LLMs with explicit system graphs scales context to the architectural level and enables continuously updated documentation. We outline an engineering pipeline: \emph{GIT $\rightarrow$ Graph Builder $\rightarrow$ Code Graph $\rightarrow$ Chunks $\rightarrow$ RAG $\rightarrow$ Local LLM $\rightarrow$ System-Aware Dialogue}.
\end{abstract}

\textbf{Keywords:} LLM; software documentation; RAG; code graph; knowledge graph; software engineering.

\section{Introduction}
A key problem in contemporary software engineering is the growing gap between code and documentation. The pace of changes outstrips documentation workflows; specifications often become outdated at the moment they are written. Not only hotfixes but also informal agreements contradicting initial requirements gradually turn into the norm. As a result, documentation ceases to mirror the system, becomes fragmented and stale, increases technical debt, slows down onboarding, and reduces architectural transparency.

This issue is recognized by both industry and academia. Recent studies report systematic misalignment between code, architectural decisions, and documentation~\cite{zhang2022inconsistent, li2023coev}. Some works estimate that up to 60--70\% of documentation becomes outdated within months unless maintenance is automated~\cite{forward2002relevance, storey2021future}.

Against this background, large language models (LLMs) are increasingly used to automate documentation generation, updates, and analysis. Prior art covers comment/docstring generation (e.g., CodeBERT~\cite{feng2020codebert}, CodeT5~\cite{wang2021codet5}), conversational access to repositories (RAG over codebases~\cite{siddiq2024rag}, GitHub Copilot Chat, Sourcegraph Cody), and structural augmentation with program graphs~\cite{peng2024structural}. The open question is whether we can build a system where documentation is \emph{born with the code} and remains current automatically.

\section{Existing Approaches (Brief)}
\subsection{Code Summarization}
Models such as CodeBERT~\cite{feng2020codebert}, CodeT5~\cite{wang2021codet5}, and GraphCodeBERT~\cite{guo2021graphcodebert} generate short textual summaries from code and AST.
\begin{itemize}[nosep]
\item \textbf{Strength:} good local quality at function/class level.
\item \textbf{Weakness:} lack of broader architectural context.
\end{itemize}

\subsection{LLMs in IDE (AI Copilots)}
IDE-integrated tools (GitHub Copilot, TabNine, JetBrains AI Assistant) leverage local project context.
\begin{itemize}[nosep]
\item \textbf{Strength:} wider local view (neighbor files, sometimes commit history)~\cite{nguyen2022copilot}.
\item \textbf{Weakness:} privacy/NDA risks~\cite{vaithilingam2022usability,ziegler2024llm}, paid APIs, context-window limits~\cite{liu2023lost}.
\end{itemize}

\subsection{Automatic API Documentation}
Tools derive OpenAPI specs from code/annotations and may augment text with LLMs.
\begin{itemize}[nosep]
\item \textbf{Strength:} robust interface documentation~\cite{lyu2024openapi}.
\item \textbf{Weakness:} no internal behavior or inter-service scenarios; specs can be incomplete~\cite{westhofen2022openapi}.
\end{itemize}

\subsection{RAG over Repositories}
A de facto standard for code Q\&A~\cite{siddiq2024rag}.
\begin{enumerate}[nosep]
\item Retrieve relevant code/doc chunks.
\item Pack them into the prompt.
\item Generate an answer with an LLM.
\end{enumerate}
\noindent
\textbf{Strength:} interactive documentation grounded in real code.\\
\textbf{Weaknesses:} retrieval quality~\cite{shrivastava2024repoRAG}; context limits and ``lost in the middle'' effect~\cite{liu2023lost}.

\subsection{Code Graph + LLM}
Represent the repository as a graph of entities and relations; feed structural subgraphs to LLMs~\cite{peng2024structural}.
\begin{itemize}[nosep]
\item \textbf{Strength:} system-level causality and precise, relevant context.
\item \textbf{Weakness:} engineering complexity of graph construction and synchronization.
\end{itemize}

\section{Pros and Cons (Summary Table)}
\begin{center}
\begin{tabular}{|p{3.8cm}|p{3cm}|p{3.2cm}|p{3.5cm}|p{2.7cm}|p{2.7cm}|p{2.7cm}|}
\hline
\textbf{Approach} & \textbf{Quality} & \textbf{Service Context} & \textbf{System Context} & \textbf{Dev Complexity} & \textbf{Cost} & \textbf{Confidentiality} \\ \hline
Code summarization & Local summaries (fn/class) & Yes (file/module) & No & Low & Low & High (offline) \\ \hline
IDE LLMs (Copilot, JetBrains AI) & Above average (neighbor files) & Yes (project structure) & Partial & Low--Mid & Mid (subscriptions, APIs) & Mid--Low (cloud) \\ \hline
Swagger/OpenAPI + AI & Strong interface docs & Partial (controllers/DTOs) & No & Mid & Low & High (on-prem) \\ \hline
RAG over codebase & High with good retrieval & Yes (module/class) & Partial (context window) & Mid & Mid--High & Mid \\ \hline
Code Graph + LLM & Highest (architectural links) & Full & Full & High & Mid--High & High (fully local) \\ \hline
\end{tabular}
\end{center}

\section{Conclusions and Direction}
LLMs excel at explaining individual code units but lack system-wide understanding; post hoc documentation does not solve staleness. We propose to bind code, architectural relations, and LLMs into a unified knowledge graph: nodes are classes, methods, modules, and APIs; edges are calls, dependencies, inheritance, events. On top of the graph, a retrieval layer selects only relevant subgraphs, and generation proceeds across levels (method $\rightarrow$ class $\rightarrow$ service $\rightarrow$ business process).

Next we plan to implement and evaluate a prototype along the pipeline: \emph{GIT $\rightarrow$ Graph Builder $\rightarrow$ Code Graph $\rightarrow$ Chunks $\rightarrow$ RAG $\rightarrow$ Local LLM $\rightarrow$ System-Aware Dialogue}. Key challenges include \textit{freshness} (incremental sync with Git), \textit{navigation} (feeding semantically coherent subgraphs), and \textit{trust} (metrics for architectural completeness and factuality).

\begin{thebibliography}{99}
\bibitem{zhang2022inconsistent} Zhang H., He Z., Liu Y., et al. An empirical study on the characteristics of inconsistent code comments. \textit{Empirical Software Engineering}. 2022;27:137.
\bibitem{li2023coev} Li Y., Wang S., Xia X., et al. How do developers update documentation? An empirical study on code-documentation co-evolution. \textit{Empirical Software Engineering}. 2023;28(10).
\bibitem{forward2002relevance} Forward A., Lethbridge T.C. The relevance of software documentation, tools and technologies: a survey. In: \textit{Proc. DocEng'02}. 2002. p. 26--33.
\bibitem{storey2021future} Storey M.-A. The future of software engineering. \textit{IEEE Software}. 2021;38(3):9--13.
\bibitem{feng2020codebert} Feng Z., Guo D., Tang D., et al. CodeBERT: A Pre-Trained Model for Programming and Natural Languages. \textit{Findings of ACL: EMNLP}. 2020.
\bibitem{wang2021codet5} Wang Y., Wang W., Jiao Y., et al. CodeT5: Unifying Code Understanding and Generation. In: \textit{EMNLP}. 2021.
\bibitem{siddiq2024rag} Siddiq M.L., et al. Retrieval-augmented generation for code-related tasks: A systematic review. arXiv:2404.05452, 2024.
\bibitem{peng2024structural} Peng H., et al. Augmenting code-language models with structural code knowledge: A review. arXiv:2402.13840, 2024.
\bibitem{guo2021graphcodebert} Guo D., Ren S., Lu S., et al. GraphCodeBERT: Pre-training Code Representations with Data Flow. In: \textit{ICLR}. 2021.
\bibitem{nguyen2022copilot} Nguyen N.M., Nadi S. An Empirical Evaluation of GitHub Copilot's Code Suggestions. In: \textit{MSR'22}. 2022.
\bibitem{vaithilingam2022usability} Vaithilingam P., Zhang T., Glassman E.L. Expectation vs. Experience: Evaluating the Usability of Code Generation Tools Powered by LLMs. In: \textit{CHI'22}. 2022.
\bibitem{ziegler2024llm} Ziegler J., et al. Large Language Models, Explained: A Survey of the Gaps, Limitations, and Ethics. arXiv:2407.16331, 2024.
\bibitem{lyu2024openapi} Lyu C., et al. Generating OpenAPI Specifications from API Documentation with LLMs. arXiv:2407.05316, 2024.
\bibitem{westhofen2022openapi} Westhofen M., et al. Evaluating the Quality of OpenAPI Specifications. In: \textit{IEEE ICWS}. 2022.
\bibitem{shrivastava2024repoRAG} Shrivastava A., et al. Repo-RAG: Enhancing Retrieval-Augmented Generation for Code Repositories. arXiv:2405.13115, 2024.
\bibitem{liu2023lost} Liu N.F., et al. Lost in the Middle: How Language Models Use Long Contexts. In: \textit{EMNLP}. 2023.
\bibitem{xu2024ckg} Xu B., Ma W., et al. A Code Knowledge Graph-Enhanced System for LLM-Based Fuzz Driver Generation. arXiv:2411.11532, 2024.
\end{thebibliography}

\end{document}
"""
open(f"{base}/main_en.tex", "w", encoding="utf-8").write(main_en)

# Provide path
print("Created LaTeX project at:", base)
