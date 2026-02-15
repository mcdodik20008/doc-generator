package com.bftcom.docgenerator.config

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.db.ChunkRepository
import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.IngestEventRepository
import com.bftcom.docgenerator.db.IngestRunRepository
import com.bftcom.docgenerator.db.IngestStepRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.chunk.Chunk
import com.bftcom.docgenerator.domain.edge.Edge
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.ingest.IngestEvent
import com.bftcom.docgenerator.domain.ingest.IngestEventLevel
import com.bftcom.docgenerator.domain.ingest.IngestRun
import com.bftcom.docgenerator.domain.ingest.IngestRunStatus
import com.bftcom.docgenerator.domain.ingest.IngestStep
import com.bftcom.docgenerator.domain.ingest.IngestStepStatus
import com.bftcom.docgenerator.domain.ingest.IngestStepType
import com.bftcom.docgenerator.domain.node.Node
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime

@Component
@Profile("demo")
class DemoDataSeeder(
    private val applicationRepo: ApplicationRepository,
    private val nodeRepo: NodeRepository,
    private val edgeRepo: EdgeRepository,
    private val chunkRepo: ChunkRepository,
    private val ingestRunRepo: IngestRunRepository,
    private val ingestStepRepo: IngestStepRepository,
    private val ingestEventRepo: IngestEventRepository,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (applicationRepo.count() > 0) {
            log.info("Demo data already seeded, skipping")
            return
        }
        log.info("Seeding demo data...")

        val app1 = seedApp1()
        val app2 = seedApp2()

        val nodes = seedNodes(app1)
        seedEdges(nodes)
        seedChunks(app1, nodes)
        seedIngestRuns(app1, app2)

        log.info(
            "Demo data seeded: 2 apps, {} nodes, edges + chunks + 3 ingest runs",
            nodes.size
        )
    }

    // ── Applications ────────────────────────────────────────────────

    private fun seedApp1(): Application = applicationRepo.save(
        Application(
            key = "doc-generator",
            name = "Doc Generator",
            description = "Code documentation generator with graph analysis and RAG",
            repoUrl = "https://github.com/bftcom/doc-generator",
            repoProvider = "github",
            repoOwner = "bftcom",
            repoName = "doc-generator",
            defaultBranch = "master",
            lastCommitSha = "bd34b948a1c2f3e4d5a6b7c8d9e0f1a2b3c4d5e6",
            lastIndexedAt = OffsetDateTime.now().minusHours(2),
            lastIndexStatus = "success",
            languages = listOf("kotlin", "sql", "yaml"),
            tags = listOf("backend", "rag", "graph"),
        )
    )

    private fun seedApp2(): Application = applicationRepo.save(
        Application(
            key = "sample-api",
            name = "Sample REST API",
            description = "Example microservice for demo purposes",
            repoUrl = "https://github.com/bftcom/sample-api",
            repoProvider = "github",
            repoOwner = "bftcom",
            repoName = "sample-api",
            defaultBranch = "main",
            lastCommitSha = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0",
            lastIndexedAt = OffsetDateTime.now().minusDays(1),
            lastIndexStatus = "failed",
            lastIndexError = "Build graph failed: OutOfMemoryError",
            languages = listOf("java", "sql"),
            tags = listOf("backend", "rest"),
        )
    )

    // ── Nodes ───────────────────────────────────────────────────────

    private fun seedNodes(app: Application): Map<String, Node> {
        val nodes = mutableMapOf<String, Node>()

        fun node(
            key: String,
            fqn: String,
            name: String?,
            kind: NodeKind,
            lang: Lang = Lang.kotlin,
            pkg: String? = null,
            parentKey: String? = null,
            filePath: String? = null,
            lineStart: Int? = null,
            lineEnd: Int? = null,
            signature: String? = null,
            sourceCode: String? = null,
            docComment: String? = null,
        ): Node {
            val saved = nodeRepo.save(
                Node(
                    application = app,
                    fqn = fqn,
                    name = name,
                    kind = kind,
                    lang = lang,
                    packageName = pkg,
                    parent = parentKey?.let { nodes[it] },
                    filePath = filePath,
                    lineStart = lineStart,
                    lineEnd = lineEnd,
                    signature = signature,
                    sourceCode = sourceCode,
                    docComment = docComment,
                )
            )
            nodes[key] = saved
            return saved
        }

        // Packages
        node("pkg-main", "com.bftcom.docgenerator", "docgenerator", NodeKind.PACKAGE, pkg = "com.bftcom")
        node("pkg-domain", "com.bftcom.docgenerator.domain", "domain", NodeKind.PACKAGE, pkg = "com.bftcom.docgenerator")

        // Classes in main package
        node(
            "RagService", "com.bftcom.docgenerator.rag.RagService", "RagService",
            NodeKind.CLASS, pkg = "com.bftcom.docgenerator.rag", parentKey = "pkg-main",
            filePath = "contexts/rag/impl/src/main/kotlin/com/bftcom/docgenerator/rag/RagService.kt",
            lineStart = 15, lineEnd = 85,
            docComment = "Main RAG service that answers user queries using graph context and vector search",
        )
        node(
            "RagService.ask", "com.bftcom.docgenerator.rag.RagService.ask", "ask",
            NodeKind.METHOD, pkg = "com.bftcom.docgenerator.rag", parentKey = "RagService",
            filePath = "contexts/rag/impl/src/main/kotlin/com/bftcom/docgenerator/rag/RagService.kt",
            lineStart = 28, lineEnd = 52,
            signature = "(query: String, sessionId: String): Flux<String>",
            docComment = "Process user query and return streaming LLM response with context from code graph",
        )
        node(
            "RagService.buildContext", "com.bftcom.docgenerator.rag.RagService.buildContext", "buildContext",
            NodeKind.METHOD, pkg = "com.bftcom.docgenerator.rag", parentKey = "RagService",
            filePath = "contexts/rag/impl/src/main/kotlin/com/bftcom/docgenerator/rag/RagService.kt",
            lineStart = 54, lineEnd = 78,
            signature = "(query: String): String",
        )

        node(
            "GraphRequestProcessor", "com.bftcom.docgenerator.rag.GraphRequestProcessor", "GraphRequestProcessor",
            NodeKind.CLASS, pkg = "com.bftcom.docgenerator.rag", parentKey = "pkg-main",
            filePath = "contexts/rag/impl/src/main/kotlin/com/bftcom/docgenerator/rag/GraphRequestProcessor.kt",
            lineStart = 12, lineEnd = 95,
            docComment = "Processes graph queries through a chain of advisors",
        )
        node(
            "GraphRequestProcessor.process", "com.bftcom.docgenerator.rag.GraphRequestProcessor.process", "process",
            NodeKind.METHOD, pkg = "com.bftcom.docgenerator.rag", parentKey = "GraphRequestProcessor",
            filePath = "contexts/rag/impl/src/main/kotlin/com/bftcom/docgenerator/rag/GraphRequestProcessor.kt",
            lineStart = 30, lineEnd = 65,
            signature = "(query: String, settings: RagSettings): QueryProcessingContext",
        )

        node(
            "EmbeddingController", "com.bftcom.docgenerator.api.embedding.EmbeddingController", "EmbeddingController",
            NodeKind.CLASS, pkg = "com.bftcom.docgenerator.api.embedding", parentKey = "pkg-main",
            filePath = "src/main/kotlin/com/bftcom/docgenerator/api/embedding/EmbeddingController.kt",
            lineStart = 18, lineEnd = 65,
        )
        node(
            "EmbeddingController.process", "com.bftcom.docgenerator.api.embedding.EmbeddingController.process", "process",
            NodeKind.ENDPOINT, pkg = "com.bftcom.docgenerator.api.embedding", parentKey = "EmbeddingController",
            filePath = "src/main/kotlin/com/bftcom/docgenerator/api/embedding/EmbeddingController.kt",
            lineStart = 32, lineEnd = 58,
            signature = "POST /api/embedding/process",
        )

        node(
            "IngestRunController", "com.bftcom.docgenerator.api.ingest.IngestRunController", "IngestRunController",
            NodeKind.CLASS, pkg = "com.bftcom.docgenerator.api.ingest", parentKey = "pkg-main",
            filePath = "src/main/kotlin/com/bftcom/docgenerator/api/ingest/IngestRunController.kt",
            lineStart = 20, lineEnd = 90,
        )
        node(
            "IngestRunController.start", "com.bftcom.docgenerator.api.ingest.IngestRunController.start", "start",
            NodeKind.ENDPOINT, pkg = "com.bftcom.docgenerator.api.ingest", parentKey = "IngestRunController",
            filePath = "src/main/kotlin/com/bftcom/docgenerator/api/ingest/IngestRunController.kt",
            lineStart = 35, lineEnd = 52,
            signature = "POST /api/ingest/start/{appId}",
        )
        node(
            "IngestRunController.getRun", "com.bftcom.docgenerator.api.ingest.IngestRunController.getRun", "getRun",
            NodeKind.ENDPOINT, pkg = "com.bftcom.docgenerator.api.ingest", parentKey = "IngestRunController",
            filePath = "src/main/kotlin/com/bftcom/docgenerator/api/ingest/IngestRunController.kt",
            lineStart = 54, lineEnd = 68,
            signature = "GET /api/ingest/runs/{runId}",
        )

        node(
            "DashboardApiController", "com.bftcom.docgenerator.api.DashboardApiController", "DashboardApiController",
            NodeKind.CLASS, pkg = "com.bftcom.docgenerator.api", parentKey = "pkg-main",
            filePath = "src/main/kotlin/com/bftcom/docgenerator/api/DashboardApiController.kt",
            lineStart = 14, lineEnd = 48,
        )
        node(
            "DashboardApiController.stats", "com.bftcom.docgenerator.api.DashboardApiController.stats", "stats",
            NodeKind.ENDPOINT, pkg = "com.bftcom.docgenerator.api", parentKey = "DashboardApiController",
            filePath = "src/main/kotlin/com/bftcom/docgenerator/api/DashboardApiController.kt",
            lineStart = 28, lineEnd = 42,
            signature = "GET /api/dashboard/stats",
        )

        node(
            "NodeRepository", "com.bftcom.docgenerator.db.NodeRepository", "NodeRepository",
            NodeKind.INTERFACE, pkg = "com.bftcom.docgenerator.db", parentKey = "pkg-main",
            filePath = "kernel/db/src/main/kotlin/com/bftcom/docgenerator/db/NodeRepository.kt",
            lineStart = 10, lineEnd = 55,
        )
        node(
            "NodeRepository.findByApplicationId", "com.bftcom.docgenerator.db.NodeRepository.findByApplicationId", "findByApplicationId",
            NodeKind.METHOD, pkg = "com.bftcom.docgenerator.db", parentKey = "NodeRepository",
            signature = "(appId: Long, pageable: Pageable): List<Node>",
        )

        // Domain classes
        node(
            "Application", "com.bftcom.docgenerator.domain.application.Application", "Application",
            NodeKind.CLASS, pkg = "com.bftcom.docgenerator.domain.application", parentKey = "pkg-domain",
            filePath = "kernel/domain/src/main/kotlin/com/bftcom/docgenerator/domain/application/Application.kt",
            lineStart = 13, lineEnd = 88,
            docComment = "Application/microservice registry entity with repo, indexing state, and RAG settings",
        )
        node(
            "Node", "com.bftcom.docgenerator.domain.node.Node", "Node",
            NodeKind.CLASS, pkg = "com.bftcom.docgenerator.domain.node", parentKey = "pkg-domain",
            filePath = "kernel/domain/src/main/kotlin/com/bftcom/docgenerator/domain/node/Node.kt",
            lineStart = 26, lineEnd = 98,
            docComment = "Graph node: class/method/table/endpoint etc. with hierarchy via parent_id",
        )
        node(
            "Edge", "com.bftcom.docgenerator.domain.edge.Edge", "Edge",
            NodeKind.CLASS, pkg = "com.bftcom.docgenerator.domain.edge", parentKey = "pkg-domain",
            filePath = "kernel/domain/src/main/kotlin/com/bftcom/docgenerator/domain/edge/Edge.kt",
            lineStart = 22, lineEnd = 64,
            docComment = "Graph edge between nodes (src -> dst) with edge kind and evidence",
        )

        return nodes
    }

    // ── Edges ───────────────────────────────────────────────────────

    private fun seedEdges(nodes: Map<String, Node>) {
        fun edge(srcKey: String, dstKey: String, kind: EdgeKind, confidence: Double? = null, strength: String? = null) {
            val src = nodes[srcKey] ?: return
            val dst = nodes[dstKey] ?: return
            edgeRepo.save(
                Edge(
                    src = src,
                    dst = dst,
                    kind = kind,
                    confidence = confidence?.let { BigDecimal.valueOf(it) },
                    relationStrength = strength ?: "normal",
                )
            )
        }

        // CONTAINS: package → class
        edge("pkg-main", "RagService", EdgeKind.CONTAINS)
        edge("pkg-main", "GraphRequestProcessor", EdgeKind.CONTAINS)
        edge("pkg-main", "EmbeddingController", EdgeKind.CONTAINS)
        edge("pkg-main", "IngestRunController", EdgeKind.CONTAINS)
        edge("pkg-main", "DashboardApiController", EdgeKind.CONTAINS)
        edge("pkg-main", "NodeRepository", EdgeKind.CONTAINS)
        edge("pkg-domain", "Application", EdgeKind.CONTAINS)
        edge("pkg-domain", "Node", EdgeKind.CONTAINS)
        edge("pkg-domain", "Edge", EdgeKind.CONTAINS)

        // CONTAINS: class → method
        edge("RagService", "RagService.ask", EdgeKind.CONTAINS)
        edge("RagService", "RagService.buildContext", EdgeKind.CONTAINS)
        edge("GraphRequestProcessor", "GraphRequestProcessor.process", EdgeKind.CONTAINS)
        edge("IngestRunController", "IngestRunController.start", EdgeKind.CONTAINS)
        edge("IngestRunController", "IngestRunController.getRun", EdgeKind.CONTAINS)

        // CALLS
        edge("RagService.ask", "RagService.buildContext", EdgeKind.CALLS, 0.95, "strong")
        edge("RagService.ask", "GraphRequestProcessor.process", EdgeKind.CALLS, 0.92, "strong")
        edge("IngestRunController.start", "NodeRepository.findByApplicationId", EdgeKind.CALLS, 0.80, "normal")

        // DEPENDS_ON
        edge("RagService", "GraphRequestProcessor", EdgeKind.DEPENDS_ON, 0.98, "strong")
        edge("EmbeddingController", "NodeRepository", EdgeKind.DEPENDS_ON, 0.85, "normal")
    }

    // ── Chunks ──────────────────────────────────────────────────────

    private fun seedChunks(app: Application, nodes: Map<String, Node>) {
        fun chunk(nodeKey: String, sourceType: String, kind: String, content: String) {
            val node = nodes[nodeKey] ?: return
            chunkRepo.save(
                Chunk(
                    application = app,
                    node = node,
                    source = sourceType,
                    kind = kind,
                    content = content,
                    contentHash = content.hashCode().toUInt().toString(16).padStart(16, '0'),
                    tokenCount = content.split(" ").size,
                )
            )
        }

        chunk(
            "RagService.ask", "code", "summary",
            "Method ask() in RagService processes user queries by building context from the code graph " +
                "and streaming responses from the LLM. It uses GraphRequestProcessor to find relevant nodes " +
                "and constructs a prompt with code context for accurate answers."
        )
        chunk(
            "RagService.buildContext", "code", "summary",
            "Method buildContext() collects relevant code snippets from the graph database, " +
                "including related nodes, edges, and documentation chunks, to build a comprehensive " +
                "context string for the LLM prompt."
        )
        chunk(
            "GraphRequestProcessor.process", "code", "summary",
            "Method process() executes a chain of QueryProcessingAdvisors to enrich the query " +
                "with graph data, embeddings, and metadata before passing it to the LLM. " +
                "Returns a QueryProcessingContext with all gathered information."
        )
        chunk(
            "EmbeddingController.process", "code", "summary",
            "Endpoint POST /api/embedding/process triggers embedding generation for code chunks. " +
                "It validates the request, finds unprocessed chunks, and sends them to the embedding model."
        )
        chunk(
            "Application", "doc", "public",
            "Application entity represents a registered microservice or repository in the system. " +
                "It stores repository URL, indexing status, RAG settings, and organization metadata. " +
                "Each application has its own set of nodes, edges, and chunks."
        )
    }

    // ── Ingest Runs ─────────────────────────────────────────────────

    private fun seedIngestRuns(app1: Application, app2: Application) {
        val now = OffsetDateTime.now()

        // Run 1: completed (app1) — all 5 steps done
        val run1 = ingestRunRepo.save(
            IngestRun(
                application = app1,
                status = IngestRunStatus.COMPLETED.name,
                triggeredBy = "manual",
                branch = "master",
                commitSha = "bd34b948a1c2",
                startedAt = now.minusHours(3),
                finishedAt = now.minusHours(2).minusMinutes(45),
                createdAt = now.minusHours(3),
            )
        )
        seedCompletedRunSteps(run1, now.minusHours(3))
        seedCompletedRunEvents(run1)

        // Run 2: failed (app1) — CHECKOUT+CLASSPATH done, BUILD_GRAPH failed
        val run2 = ingestRunRepo.save(
            IngestRun(
                application = app1,
                status = IngestRunStatus.FAILED.name,
                triggeredBy = "manual",
                branch = "feature/new-parser",
                commitSha = "e574d3c1a2b3",
                errorMessage = "BUILD_GRAPH failed: OutOfMemoryError: Java heap space",
                startedAt = now.minusDays(1),
                finishedAt = now.minusDays(1).plusMinutes(8),
                createdAt = now.minusDays(1),
            )
        )
        seedFailedRunSteps(run2, now.minusDays(1))
        seedFailedRunEvents(run2)

        // Run 3: completed (app2)
        val run3 = ingestRunRepo.save(
            IngestRun(
                application = app2,
                status = IngestRunStatus.COMPLETED.name,
                triggeredBy = "webhook",
                branch = "main",
                commitSha = "a1b2c3d4e5f6",
                startedAt = now.minusDays(2),
                finishedAt = now.minusDays(2).plusMinutes(12),
                createdAt = now.minusDays(2),
            )
        )
        seedCompletedRunSteps(run3, now.minusDays(2))
    }

    private fun seedCompletedRunSteps(run: IngestRun, baseTime: OffsetDateTime) {
        val steps = IngestStepType.entries
        var t = baseTime
        for (step in steps) {
            val duration = when (step) {
                IngestStepType.CHECKOUT -> 15L
                IngestStepType.RESOLVE_CLASSPATH -> 45L
                IngestStepType.BUILD_LIBRARY -> 30L
                IngestStepType.BUILD_GRAPH -> 120L
                IngestStepType.LINK -> 60L
            }
            val stepEnd = t.plusSeconds(duration)
            ingestStepRepo.save(
                IngestStep(
                    run = run,
                    stepType = step.name,
                    status = IngestStepStatus.COMPLETED.name,
                    itemsProcessed = (10..50).random(),
                    itemsTotal = (10..50).random(),
                    startedAt = t,
                    finishedAt = stepEnd,
                )
            )
            t = stepEnd
        }
    }

    private fun seedFailedRunSteps(run: IngestRun, baseTime: OffsetDateTime) {
        // CHECKOUT — completed
        ingestStepRepo.save(
            IngestStep(
                run = run,
                stepType = IngestStepType.CHECKOUT.name,
                status = IngestStepStatus.COMPLETED.name,
                itemsProcessed = 1,
                itemsTotal = 1,
                startedAt = baseTime,
                finishedAt = baseTime.plusSeconds(12),
            )
        )
        // RESOLVE_CLASSPATH — completed
        ingestStepRepo.save(
            IngestStep(
                run = run,
                stepType = IngestStepType.RESOLVE_CLASSPATH.name,
                status = IngestStepStatus.COMPLETED.name,
                itemsProcessed = 23,
                itemsTotal = 23,
                startedAt = baseTime.plusSeconds(12),
                finishedAt = baseTime.plusSeconds(55),
            )
        )
        // BUILD_LIBRARY — completed
        ingestStepRepo.save(
            IngestStep(
                run = run,
                stepType = IngestStepType.BUILD_LIBRARY.name,
                status = IngestStepStatus.COMPLETED.name,
                itemsProcessed = 8,
                itemsTotal = 8,
                startedAt = baseTime.plusSeconds(55),
                finishedAt = baseTime.plusMinutes(2),
            )
        )
        // BUILD_GRAPH — failed
        ingestStepRepo.save(
            IngestStep(
                run = run,
                stepType = IngestStepType.BUILD_GRAPH.name,
                status = IngestStepStatus.FAILED.name,
                errorMessage = "OutOfMemoryError: Java heap space",
                itemsProcessed = 142,
                itemsTotal = 500,
                startedAt = baseTime.plusMinutes(2),
                finishedAt = baseTime.plusMinutes(7),
            )
        )
        // LINK — skipped
        ingestStepRepo.save(
            IngestStep(
                run = run,
                stepType = IngestStepType.LINK.name,
                status = IngestStepStatus.SKIPPED.name,
            )
        )
    }

    private fun seedCompletedRunEvents(run: IngestRun) {
        val base = run.startedAt ?: OffsetDateTime.now()
        val events = listOf(
            Triple(IngestEventLevel.INFO, IngestStepType.CHECKOUT, "Cloning repository from https://github.com/bftcom/doc-generator"),
            Triple(IngestEventLevel.INFO, IngestStepType.CHECKOUT, "Checked out branch master at bd34b94"),
            Triple(IngestEventLevel.INFO, IngestStepType.RESOLVE_CLASSPATH, "Resolving Gradle classpath for 16 modules"),
            Triple(IngestEventLevel.WARN, IngestStepType.RESOLVE_CLASSPATH, "Module 'e2e' skipped: not a JVM module"),
            Triple(IngestEventLevel.INFO, IngestStepType.BUILD_LIBRARY, "Analyzing 12 library dependencies"),
            Triple(IngestEventLevel.INFO, IngestStepType.BUILD_GRAPH, "Building code graph: 342 files, 1847 symbols"),
            Triple(IngestEventLevel.INFO, IngestStepType.BUILD_GRAPH, "Graph built: 1847 nodes, 3291 edges"),
            Triple(IngestEventLevel.INFO, IngestStepType.LINK, "Linking cross-module references: 156 links resolved"),
        )
        for ((i, triple) in events.withIndex()) {
            val (level, stepType, message) = triple
            ingestEventRepo.save(
                IngestEvent(
                    run = run,
                    stepType = stepType.name,
                    level = level.name,
                    message = message,
                    createdAt = base.plusSeconds(i * 20L),
                )
            )
        }
    }

    private fun seedFailedRunEvents(run: IngestRun) {
        val base = run.startedAt ?: OffsetDateTime.now()
        val events = listOf(
            Triple(IngestEventLevel.INFO, IngestStepType.CHECKOUT, "Cloning repository from https://github.com/bftcom/doc-generator"),
            Triple(IngestEventLevel.INFO, IngestStepType.CHECKOUT, "Checked out branch feature/new-parser at e574d3c"),
            Triple(IngestEventLevel.INFO, IngestStepType.RESOLVE_CLASSPATH, "Resolving Gradle classpath for 16 modules"),
            Triple(IngestEventLevel.INFO, IngestStepType.BUILD_LIBRARY, "Analyzing 12 library dependencies"),
            Triple(IngestEventLevel.INFO, IngestStepType.BUILD_GRAPH, "Building code graph: 500 files"),
            Triple(IngestEventLevel.ERROR, IngestStepType.BUILD_GRAPH, "OutOfMemoryError: Java heap space — processed 142 of 500 files"),
        )
        for ((i, triple) in events.withIndex()) {
            val (level, stepType, message) = triple
            ingestEventRepo.save(
                IngestEvent(
                    run = run,
                    stepType = stepType.name,
                    level = level.name,
                    message = message,
                    createdAt = base.plusSeconds(i * 25L),
                )
            )
        }
    }
}
