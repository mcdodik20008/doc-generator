# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Doc-Generator is a Kotlin/Spring Boot application that analyzes source code repositories, builds knowledge graphs, generates AI-powered documentation, and supports RAG (Retrieval-Augmented Generation) queries. It parses Kotlin AST, creates node/edge graphs, generates vector embeddings, and uses LLMs (via Ollama) for documentation generation.

Language: Kotlin 2.0.21 | Framework: Spring Boot 3.5.6 | JVM: Java 21 | DB: PostgreSQL 16 + pgvector

## Build & Run Commands

```bash
# Build (Windows - use gradlew.bat)
gradlew.bat build                    # Full build with tests
gradlew.bat build -x test            # Skip tests
gradlew.bat :contexts:graph:impl:build  # Single module

# Run
gradlew.bat bootRun                  # Requires PostgreSQL + Ollama running
gradlew.bat bootRun --args="--spring.profiles.active=demo"  # Embedded PG, no Ollama needed

# Tests
gradlew.bat test                     # All tests
gradlew.bat test --tests "com.bftcom.docgenerator.SomeTest"  # Single test class
gradlew.bat test --tests "com.bftcom.docgenerator.SomeTest.testMethod"  # Single method
gradlew.bat :contexts:graph:impl:test   # Module-specific tests

# Code coverage
gradlew.bat koverReport              # Report at build/reports/kover/

# E2E tests (Playwright)
cd e2e && npm test                   # Requires app running on localhost:8080

# Docker services (PostgreSQL, Ollama, Redis)
docker compose up -d
```

## Architecture

### Multi-Module Gradle Project (API/Impl Pattern)

Each domain context is split into an `api` module (interfaces + DTOs) and an `impl` module (implementations). The root module assembles everything into a Spring Boot application.

```
kernel/
  domain/     # JPA entities (Node, Edge, Application, Chunk, NodeDoc, IngestRun)
  db/         # Spring Data JPA repositories
  shared/     # Shared utilities and constants

contexts/
  graph/      # Code graph building — Kotlin AST parsing, node/edge creation
  chunking/   # Document chunking strategies
  embedding/  # Vector embeddings via Ollama (bge-m3)
  rag/        # RAG query processing with FSM pipeline
  git/        # Git/GitLab/GitHub repository operations
  library/    # External library analysis (JAR bytecode parsing)
  ai/         # LLM client wrappers (coder + talker models)
  postprocess/# Post-processing utilities

src/main/kotlin/.../
  api/        # REST controllers (graph, ingest, chunk, rag, embedding, analysis, audit)
  config/     # Spring configuration beans
  configprops/# @ConfigurationProperties classes
  analysis/   # Change impact analysis logic
```

### Key Domain Model (kernel/domain)

- **Node** — code element (class, method, field, package, endpoint, topic, etc.) identified by FQN per application
- **Edge** — typed relationship between nodes (CALLS, INHERITS, USES, THROWS, etc.)
- **Application** — represents a codebase/repository
- **NodeDoc** — AI-generated documentation with status tracking
- **Chunk** — document segments with vector embeddings
- **IngestRun** — repository ingestion execution with step/event logging

### Graph Building (3-Stage Process)

Documented in `graph_building_algorithm.md`:
1. **Stage 0**: Library analysis (JAR bytecode → LibraryNode)
2. **Stage 1**: Node creation (Kotlin AST → Node entities via `KotlinToDomainVisitor`)
3. **Stage 2**: Edge linking (relationship building between nodes)

Triggered via Spring Events: `GraphBuildRequestedEvent`, `LinkRequestedEvent`, `LibraryBuildRequestedEvent`

### Integration Node Detection

Synthetic integration nodes (`meta["synthetic"] = true`) represent cross-app communication:
- FQN patterns: `infra:http:METHOD:URL`, `infra:kafka:topic:NAME`, `infra:camel:uri:URI`
- NodeKind: ENDPOINT or TOPIC

### Cross-Cutting Concerns

- **Auth**: API key-based (`ApiKeyAuthenticationFilter`), disabled by default in dev
- **Rate Limiting**: Resilience4j (`RateLimitFilter`)
- **Audit**: `AuditWebFilter` intercepts all requests
- **Metrics**: Micrometer + Prometheus at `/actuator/prometheus`
- **API Docs**: SpringDoc OpenAPI at `/swagger`

## Configuration

Main config: `src/main/resources/application.yml`

Profiles:
- **(default)**: Full features, needs PostgreSQL on port 5434 + Ollama on 11434
- **demo**: Embedded PostgreSQL, no Ollama/pgvector/Docker required
- Key env vars: `DB_HOST`, `DB_PORT`, `OLLAMA_HOSTNAME`, `GITLAB_URL`, `GITLAB_TOKEN`

Custom properties under `docgen.*`:
- `docgen.graph.max-nodes` — OOM protection (default 100,000)
- `docgen.rag.processing-timeout-seconds` — RAG pipeline timeout
- `docgen.library.whitelist` — groupId prefixes for library analysis
- `docgen.security.enabled` — toggle authentication
- `docgen.embed.enabled` — toggle vector embedding

## Database

PostgreSQL 16 with pgvector extension. Schema managed by Liquibase (`kernel/domain/src/main/resources/liquibase/`). Docker Compose exposes PG on port **5434** (not default 5432).

## Frontend

Thymeleaf templates in `src/main/resources/templates/`. Key pages:
- `/` — Dashboard
- `/graph` — Code graph visualization (Cytoscape.js, single-app + cross-app views)
- `/chat` — AI chat interface
- `/ingest` — Repository ingestion UI

## Conventions

- Code comments and AI system prompts are in Russian
- The project uses `gradlew.bat` on Windows (the development platform)
- Test framework: JUnit 5 + MockK/Mockito + AssertJ + TestContainers
- Module dependencies flow: `impl → api → kernel`
