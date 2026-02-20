# Doc Generator

AI-powered documentation generator for software projects. Automatically analyzes code, builds dependency graphs, and generates comprehensive technical documentation using LLM.

[![Build](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-blue.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)]()

## 🎯 Key Features

### Core Functionality
- **Automatic Code Analysis**: Parses Java/Kotlin projects and extracts structure
- **Dependency Graph**: Builds interactive visualization of code dependencies
- **LLM-Powered Documentation**: Generates human-readable docs using Ollama/OpenAI
- **Semantic Search**: RAG-based Q&A system with pgvector embeddings
- **Cross-Application Integration**: Visualizes integrations and detects HTTP/Kafka/Camel dependencies

### Production-Ready Features (P0) ✨

#### 🔐 Security & Authentication
- **API Key Management**: SHA-256 hashed keys with scopes and expiration
- **X-API-Key Authentication**: Secure all /api/ endpoints
- **Audit Logging**: Immutable audit trail for all mutations
- **Configurable Security**: Enable/disable for dev/prod environments

#### 🛡️ Resilience & Reliability
- **Circuit Breaker**: Protects LLM calls with Resilience4j
  - Auto-recovery from failures
  - Configurable thresholds and timeouts
  - Fallback strategies
- **Rate Limiting**: Redis-backed sliding window rate limiter
  - Per-API-key limits
  - Different limits for endpoint types
  - Cluster-ready

#### 📊 Observability
- **Health Checks**: Circuit breaker state in `/actuator/health`
- **Prometheus Metrics**: Resilience4j metrics auto-exported
- **Structured Audit Logs**: Searchable via API

See [CIRCUIT_BREAKER.md](CIRCUIT_BREAKER.md) and [RATE_LIMITING.md](RATE_LIMITING.md) for details.

## 🏗️ Architecture

```
┌─────────────────┐
│   Web UI        │  Thymeleaf pages (Dashboard, Graph, Chat)
│   /dashboard    │
│   /graph        │
│   /chat         │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   REST API      │  /api/v1/*
│   Controllers   │  ├─ /ingest      (Ingestion pipeline)
│                 │  ├─ /graph       (Dependency graph)
│                 │  ├─ /rag         (Q&A system)
│                 │  ├─ /embedding   (Vector search)
│                 │  ├─ /api-keys    (Key management)
│                 │  └─ /audit-logs  (Audit query)
└────────┬────────┘
         │
    ┌────┴─────┬──────────┬──────────┐
    ▼          ▼          ▼          ▼
┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
│ Ingest │ │  RAG   │ │ Graph  │ │ Embed  │  Business Logic (contexts)
└───┬────┘ └───┬────┘ └───┬────┘ └───┬────┘
    │          │          │          │
    └──────────┴──────────┴──────────┘
                  │
         ┌────────┴─────────┐
         ▼                  ▼
    ┌─────────┐      ┌──────────┐
    │ LLM     │      │ pgvector │  External Services
    │ (Ollama)│      │ (Embeddings)
    └─────────┘      └──────────┘
         ▲
         │ (protected by Circuit Breaker)
         │
    ┌────┴──────┐
    │ Resilience│
    │ - Retry   │
    │ - Bulkhead│
    └───────────┘
```

### Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Backend** | Kotlin + Spring Boot 3.5.6 | REST API, WebFlux |
| **Database** | PostgreSQL + pgvector | Persistence + embeddings |
| **LLM** | Ollama (qwen2.5) | Code documentation |
| **Embeddings** | bge-m3 / mxbai-embed-large | Semantic search |
| **Cache/Rate Limit** | Redis | Distributed state |
| **Security** | Spring Security | API key auth |
| **Resilience** | Resilience4j | Circuit breaker, retry |
| **Frontend** | Thymeleaf + Cytoscape.js | Web UI + graph viz |
| **Migrations** | Liquibase | Schema versioning |
| **Monitoring** | Actuator + Prometheus | Health & metrics |

## 🚀 Quick Start

### Prerequisites

- **Java 21+**
- **Docker & Docker Compose** (for dependencies)
- **Git**

### 1. Clone Repository

```bash
git clone https://github.com/your-org/doc-generator.git
cd doc-generator
```

### 2. Start Dependencies

```bash
docker-compose up -d
```

This starts:
- PostgreSQL (port 5434)
- Redis (port 6379)
- Ollama (port 11434)

### 3. Pull LLM Models

```bash
# Coder model (for technical docs)
docker exec -it ollama ollama pull qwen2.5-coder:14b

# Talker model (for human-readable docs)
docker exec -it ollama ollama pull qwen2.5:14b-instruct

# Embedding model
docker exec -it ollama ollama pull bge-m3
```

### 4. Build & Run

```bash
./gradlew bootRun
```

### 5. Create API Key

```bash
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-first-key",
    "scopes": ["read", "write"],
    "expiresInDays": 30
  }'
```

Response:
```json
{
  "id": 1,
  "name": "my-first-key",
  "rawKey": "dg_a1b2c3d4e5f6...",  // ⚠️ Save this! Shown only once
  "scopes": ["read", "write"],
  "expiresAt": "2026-03-21T10:30:00Z"
}
```

### 6. Test API

```bash
export API_KEY="dg_a1b2c3d4e5f6..."

# Ingest a project
curl -X POST "http://localhost:8080/api/v1/ingest/reindex/1" \
  -H "X-API-Key: $API_KEY"

# Query documentation
curl -X POST "http://localhost:8080/api/v1/rag/ask" \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": 1,
    "query": "How does authentication work?"
  }'
```

### 7. Open Web UI

Visit:
- **Dashboard**: http://localhost:8080/dashboard
- **Graph View**: http://localhost:8080/graph
- **Chat UI**: http://localhost:8080/chat
- **Swagger**: http://localhost:8080/swagger-ui.html
- **Health**: http://localhost:8080/actuator/health

## ⚙️ Configuration

### Environment Variables

Create `.env` file:

```bash
# Database
DB_HOST=localhost
DB_PORT=5434
DB_NAME=docgen
DB_USERNAME=docgen
DB_PASSWORD=docgen

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DATABASE=0

# Ollama LLM
OLLAMA_HOSTNAME=localhost
OLLAMA_PORT=11434
OLLAMA_EMBEDDING_MODEL=bge-m3
CODER_MODEL=qwen2.5-coder:14b
TALKER_MODEL=qwen2.5:14b-instruct

# Git Providers
GITHUB_TOKEN=ghp_yourtoken
GITLAB_TOKEN=glpat_yourtoken
GITLAB_URL=https://gitlab.company.com

# Security (disable for dev, enable for prod)
DOCGEN_SECURITY_ENABLED=false
DOCGEN_RATE_LIMIT_ENABLED=false
```

### application.yml

See [application.yml](src/main/resources/application.yml) for full configuration.

Key sections:
- `spring.datasource` - PostgreSQL connection
- `spring.data.redis` - Redis connection
- `spring.ai.ollama` - LLM models
- `docgen.security` - Auth toggle
- `docgen.rate-limit` - Rate limiting toggle
- `docgen.rag` - RAG pipeline timeouts

## 🔑 API Key Management

### Creating Keys

```bash
# Development key (no expiration)
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H "Content-Type: application/json" \
  -d '{"name": "dev-key", "scopes": ["read", "write"]}'

# Production key (30 days)
curl -X POST http://localhost:8080/api/v1/api-keys \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-integration",
    "scopes": ["read"],
    "expiresInDays": 30
  }'
```

### Listing Keys

```bash
curl http://localhost:8080/api/v1/api-keys
```

### Revoking Keys

```bash
curl -X DELETE http://localhost:8080/api/v1/api-keys/1
```

### Scopes

| Scope | Description |
|-------|-------------|
| `read` | GET requests (query docs, search) |
| `write` | POST/PUT/DELETE (ingest, reindex) |
| `admin` | All operations including key management |

See [TESTING_GUIDE.md](TESTING_GUIDE.md) for detailed testing procedures.

## 📚 API Endpoints

### Ingestion

```
POST   /api/v1/ingest/reindex/{appId}     Reindex application
GET    /api/v1/dashboard/applications     List applications
GET    /api/v1/dashboard/stats            Dashboard statistics
```

### Graph & Search

```
GET    /api/v1/graph/chunks               Get chunk graph
GET    /api/v1/graph/cross-app            Cross-application graph
POST   /api/v1/embedding/search           Semantic search
```

### Analysis & Metrics

```
GET    /api/v1/analysis/impact            Evaluate change impact of a component
```

### Integration Points

```
GET    /api/v1/integration/methods/by-url         Find methods calling a specific URL
GET    /api/v1/integration/methods/by-kafka-topic Find methods using a Kafka topic
GET    /api/v1/integration/methods/by-camel-uri   Find methods using a Camel URI
GET    /api/v1/integration/method/summary         Get integration summary for a method
GET    /api/v1/integration/parent-clients         Find parent clients in a library
```

### RAG (Q&A)

```
POST   /api/v1/rag/ask                    Ask question about code
```

### Management

```
GET    /api/v1/api-keys                   List API keys
POST   /api/v1/api-keys                   Create API key
DELETE /api/v1/api-keys/{id}              Revoke API key

GET    /api/v1/audit-logs                 Search audit logs
       ?user=alice&action=INGEST_START&from=2026-02-01T00:00:00Z
```

### Health & Metrics

```
GET    /actuator/health                   Health check + circuit breaker status
GET    /actuator/metrics                  Prometheus metrics
GET    /actuator/prometheus               Prometheus endpoint
```

## 🔍 Usage Examples

### 1. Ingest a Project

```kotlin
// Add application to database first
val app = Application(
    name = "my-service",
    gitUrl = "https://github.com/company/my-service",
    branch = "main"
)
applicationRepository.save(app)

// Trigger ingestion
POST /api/v1/ingest/reindex/1
```

### 2. Ask Questions

```bash
curl -X POST http://localhost:8080/api/v1/rag/ask \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": 1,
    "query": "How does the authentication flow work?",
    "mode": "FULL",
    "maxChunks": 5
  }'
```

### 3. Search Code

```bash
curl -X POST http://localhost:8080/api/v1/embedding/search \
  -H "X-API-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": 1,
    "query": "user authentication",
    "topK": 10
  }'
```

### 4. View Dependency Graph

Open http://localhost:8080/graph and select your application.

### 5. Cross-Application Integrations

Visualizing integratons between apps:
```bash
curl "http://localhost:8080/api/v1/graph/cross-app?applicationIds=1,2,3&types=HTTP,KAFKA" \
  -H "X-API-Key: $API_KEY"
```

Finding exact methods using a Kafka topic:
```bash
curl "http://localhost:8080/api/v1/integration/methods/by-kafka-topic?topic=orders-events" \
  -H "X-API-Key: $API_KEY"
```

## 🧪 Testing

See comprehensive [TESTING_GUIDE.md](TESTING_GUIDE.md).

Quick test commands:

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "CrossAppGraphServiceImplTest"

# Build without tests
./gradlew build -x test

# Check coverage
./gradlew koverHtmlReport
# Open build/reports/kover/html/index.html
```

## 🐛 Troubleshooting

### LLM not responding

```bash
# Check Ollama
docker ps | grep ollama
curl http://localhost:11434/api/tags

# Check circuit breaker
curl http://localhost:8080/actuator/health | jq '.components.llmCircuitBreaker'
```

### Rate limit issues

```bash
# Check Redis
docker exec -it redis redis-cli
> KEYS rate_limit:*
> ZCARD rate_limit:my-key:/api/v1/rag

# Disable rate limiting
export DOCGEN_RATE_LIMIT_ENABLED=false
```

### Database connection failed

```bash
# Check PostgreSQL
docker ps | grep postgres
docker logs doc-generator-postgres

# Test connection
psql -h localhost -p 5434 -U docgen -d docgen
```

### API Key authentication failing

```bash
# Check security is enabled
curl http://localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("applicationConfig"))'

# Disable security for debugging
export DOCGEN_SECURITY_ENABLED=false
```

See [CIRCUIT_BREAKER.md](CIRCUIT_BREAKER.md) for LLM resilience troubleshooting.

## 📊 Monitoring

### Circuit Breaker Dashboard

```bash
# Check state
curl http://localhost:8080/actuator/health | jq '.components.llmCircuitBreaker'

# Expected output (healthy):
{
  "status": "UP",
  "details": {
    "state": "CLOSED",
    "failureRate": 0.0,
    "numberOfBufferedCalls": 10
  }
}
```

### Rate Limiting Metrics

```bash
# Redis stats
redis-cli INFO stats

# Rate limit keys
redis-cli KEYS "rate_limit:*"
```

### Prometheus Metrics

Add to `prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'doc-generator'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

Key metrics:
- `resilience4j_circuitbreaker_state`
- `resilience4j_retry_calls`
- `resilience4j_bulkhead_available_concurrent_calls`
- `http_server_requests_seconds` (endpoint latency)

## 🏗️ Development

### Project Structure

```
doc-generator/
├── src/main/kotlin/com/bftcom/docgenerator/
│   ├── api/              # REST controllers
│   │   ├── apikey/       # API key management
│   │   ├── audit/        # Audit log API
│   │   ├── graph/        # Graph endpoints
│   │   ├── ingest/       # Ingestion API
│   │   └── rag/          # RAG Q&A API
│   ├── config/           # Spring configuration
│   │   ├── SecurityConfig.kt
│   │   ├── RateLimitFilter.kt
│   │   ├── ResilientLlmWrapper.kt
│   │   └── ApiKeyAuthenticationFilter.kt
│   └── ...
├── contexts/             # Business logic modules
│   ├── ai/               # LLM clients
│   ├── graph/            # Graph building
│   ├── rag/              # RAG pipeline
│   ├── embedding/        # Vector embeddings
│   └── ...
├── kernel/
│   ├── domain/           # Entities (Node, Edge, Chunk, etc.)
│   ├── db/               # Repositories
│   └── shared/           # Shared utilities
└── src/main/resources/
    ├── templates/        # Thymeleaf pages
    ├── liquibase/        # DB migrations
    └── application.yml   # Configuration
```

### Adding New Features

1. Create domain entities in `kernel/domain`
2. Add repository in `kernel/db`
3. Create Liquibase migration in `kernel/domain/resources/liquibase/migration/`
4. Implement business logic in `contexts/`
5. Add REST controller in `src/main/kotlin/.../api/`
6. Add tests
7. Update documentation

### Code Style

- Kotlin 2.0.21
- ktlint (IntelliJ default style)
- Prefer immutability
- Use data classes for DTOs
- Document public APIs

## 📖 Documentation

- [README_TECHNICAL.md](README_TECHNICAL.md) - Technical architecture deep-dive
- [CIRCUIT_BREAKER.md](CIRCUIT_BREAKER.md) - LLM resilience patterns
- [RATE_LIMITING.md](RATE_LIMITING.md) - API rate limiting
- [TESTING_GUIDE.md](TESTING_GUIDE.md) - Comprehensive test guide
- [CROSS_APP_IMPLEMENTATION_SUMMARY.md](CROSS_APP_IMPLEMENTATION_SUMMARY.md) - Cross-app feature details

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📝 License

This project is licensed under the MIT License.

## 🙋 Support

- **Issues**: https://github.com/your-org/doc-generator/issues
- **Discussions**: https://github.com/your-org/doc-generator/discussions
- **Email**: support@yourcompany.com

## 🎉 Acknowledgments

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Ollama](https://ollama.ai/)
- [Resilience4j](https://resilience4j.readme.io/)
- [pgvector](https://github.com/pgvector/pgvector)
- [Cytoscape.js](https://js.cytoscape.org/)

---

**Made with ❤️ by the Doc Generator Team**
