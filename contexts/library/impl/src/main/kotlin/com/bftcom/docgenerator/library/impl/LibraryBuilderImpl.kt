package com.bftcom.docgenerator.library.impl

import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.db.LibraryRepository
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.library.Library
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.library.api.BytecodeParser
import com.bftcom.docgenerator.library.api.LibraryBuilder
import com.bftcom.docgenerator.library.api.LibraryBuildResult
import com.bftcom.docgenerator.library.api.LibraryCoordinate
import com.bftcom.docgenerator.library.api.RawLibraryNode
import com.bftcom.docgenerator.library.api.bytecode.HttpBytecodeAnalyzer
import com.bftcom.docgenerator.library.impl.coordinate.LibraryCoordinateParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import org.springframework.context.annotation.Lazy
import org.springframework.transaction.annotation.Propagation

/**
 * Реализация построителя графа библиотек.
 * Оркестрирует парсинг координат, байткода и сохранение в БД.
 */
@Service
class LibraryBuilderImpl(
    private val coordinateParser: LibraryCoordinateParser,
    private val bytecodeParser: BytecodeParser,
    private val httpBytecodeAnalyzer: HttpBytecodeAnalyzer,
    private val libraryRepo: LibraryRepository,
    private val libraryNodeRepo: LibraryNodeRepository,
    private val objectMapper: ObjectMapper,
    @Lazy private val self: LibraryBuilderImpl?, // важно для вызова @Transactional-метода
) : LibraryBuilder {
    private val log = LoggerFactory.getLogger(javaClass)

    private val whiteList = listOf(
        "com.bftcom",
        "ru.bftcom",
        "ru.supercode",
        "rrbpm"
    )

    data class SingleLibraryResult(
        val librariesProcessed: Int = 0,
        val librariesSkipped: Int = 0,
        val nodesCreated: Int = 0,
    )

    override fun buildLibraries(classpath: List<File>): LibraryBuildResult {
        log.info("Starting library build for {} classpath entries", classpath.size)

        var librariesProcessed = 0
        var librariesSkipped = 0
        var nodesCreated = 0
        val errors = mutableListOf<String>()

        val jarFiles = classpath.filter { it.name.endsWith(".jar", ignoreCase = true) }
        log.debug("Found {} jar files in classpath", jarFiles.size)

        for (jarFile in jarFiles) {
            try {
                val result = self?.processSingleLibrary(jarFile) ?: SingleLibraryResult()

                librariesProcessed += result.librariesProcessed
                librariesSkipped += result.librariesSkipped
                nodesCreated += result.nodesCreated
            } catch (e: Exception) {
                val errorMsg = "Failed to process jar ${jarFile.name}: ${e.message}"
                log.error(errorMsg, e)
                errors.add(errorMsg)
            }
        }

        val result = LibraryBuildResult(
            librariesProcessed = librariesProcessed,
            nodesCreated = nodesCreated,
            librariesSkipped = librariesSkipped,
            errors = errors,
        )

        log.info(
            "Library build completed: processed={}, skipped={}, nodes={}, errors={}",
            result.librariesProcessed,
            result.librariesSkipped,
            result.nodesCreated,
            result.errors.size,
        )

        return result
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processSingleLibrary(jarFile: File): SingleLibraryResult {
        var librariesProcessed = 0
        var librariesSkipped = 0
        var nodesCreated = 0

        // 1. Извлекаем координаты
        val coordinate = coordinateParser.parseCoordinate(jarFile)
        if (coordinate == null) {
            log.debug("Skipping jar without coordinates: {}", jarFile.name)
            return SingleLibraryResult(
                librariesProcessed = 0,
                librariesSkipped = 0,
                nodesCreated = 0,
            )
        }

        // 1.1. Проверяем, является ли библиотека библиотекой компании
        if (!isCompanyLibrary(coordinate)) {
            log.debug(
                "Skipping external library (not company): {} (groupId: {})",
                jarFile.name,
                coordinate.groupId,
            )
            return SingleLibraryResult(
                librariesProcessed = 0,
                librariesSkipped = 1, // Считаем как пропущенную
                nodesCreated = 0,
            )
        }

        // 2. Проверяем, существует ли библиотека
        val existingLibrary = libraryRepo.findByCoordinate(coordinate.coordinate)
        val library = existingLibrary ?: run {
            librariesProcessed++
            Library(
                coordinate = coordinate.coordinate,
                groupId = coordinate.groupId,
                artifactId = coordinate.artifactId,
                version = coordinate.version,
                kind = determineLibraryKind(coordinate),
                metadata = emptyMap(),
            ).also {
                libraryRepo.save(it)
                log.debug("Created library: {}", coordinate.coordinate)
            }
        }

        if (existingLibrary != null) {
            librariesSkipped++
            log.debug("Library already exists, skipping: {}", coordinate.coordinate)
            // Можно добавить логику обновления нод при изменении версии, если понадобится
            return SingleLibraryResult(
                librariesProcessed = librariesProcessed,
                librariesSkipped = librariesSkipped,
                nodesCreated = nodesCreated,
            )
        }

        // 3. Парсим байткод
        val rawNodes = bytecodeParser.parseJar(jarFile)
        log.debug("Parsed {} raw nodes from jar: {}", rawNodes.size, jarFile.name)

        // 3.1. Анализируем HTTP-вызовы (байткод-анализ)
        val analysisResult = try {
            httpBytecodeAnalyzer.analyzeJar(jarFile)
        } catch (e: Exception) {
            log.warn("HTTP bytecode analysis failed for {}: {}", jarFile.name, e.message)
            null
        }

        // 4. Сохраняем LibraryNode
        val savedNodes = saveLibraryNodes(library, rawNodes, analysisResult)
        nodesCreated += savedNodes

        log.info(
            "Processed library: {} ({} nodes)",
            coordinate.coordinate,
            savedNodes,
        )

        return SingleLibraryResult(
            librariesProcessed = librariesProcessed,
            librariesSkipped = librariesSkipped,
            nodesCreated = nodesCreated,
        )
    }

    private fun saveLibraryNodes(
        library: Library,
        rawNodes: List<RawLibraryNode>,
        analysisResult: com.bftcom.docgenerator.library.api.bytecode.BytecodeAnalysisResult?,
    ): Int {
        val startedNs = System.nanoTime()

        var saved = 0
        val parentMap = mutableMapOf<String, LibraryNode>()

        var tFindExisting = 0L
        var tSave = 0L
        var tCreate = 0L

        fun now() = System.nanoTime()

        // Создаем мапу методов с интеграционными вызовами для быстрой проверки
        val integrationMethods = analysisResult?.methodSummaries?.keys?.map { methodId ->
            "${methodId.ownerFqn.replace('/', '.')}.${methodId.name}"
        }?.toSet() ?: emptySet()

        // Фильтруем узлы, оставляем только значимые для интеграции
        val allClassNodes = rawNodes.filter { it.kind in setOf(NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.ENUM) }
        val relevantClassNodes = allClassNodes.filter { raw ->
            isIntegrationRelevantClass(raw)
        }
        
        // Создаем множество FQN релевантных классов для быстрой проверки родительских классов
        val relevantClassFqns = relevantClassNodes.map { it.fqn }.toSet()
        
        val allMemberNodes = rawNodes.filter { it.kind in setOf(NodeKind.METHOD, NodeKind.FIELD) }
        val relevantMemberNodes = allMemberNodes.filter { raw ->
            isIntegrationRelevantMember(raw, relevantClassFqns, integrationMethods, analysisResult)
        }

        log.info(
            "Filtering library nodes: total classes={}, relevant classes={}, " +
                    "total members={}, relevant members={}",
            allClassNodes.size, relevantClassNodes.size,
            allMemberNodes.size, relevantMemberNodes.size
        )

        // Сначала классы
        for (raw in relevantClassNodes) {

            val t0 = now()
            val existing = libraryNodeRepo.findByLibraryIdAndFqn(library.id!!, raw.fqn)
            tFindExisting += now() - t0

            if (existing == null) {
                val t1 = now()
                val node = createLibraryNode(library, raw, null, analysisResult)
                tCreate += now() - t1

                val t2 = now()
                val savedNode = libraryNodeRepo.save(node)
                tSave += now() - t2

                parentMap[raw.fqn] = savedNode
                saved++
            } else {
                parentMap[raw.fqn] = existing
            }
        }

        // Методы и поля
        for (raw in relevantMemberNodes) {

            val t0 = now()
            val existing = libraryNodeRepo.findByLibraryIdAndFqn(library.id!!, raw.fqn)
            tFindExisting += now() - t0

            if (existing == null) {
                val parent = raw.parentFqn?.let { parentMap[it] }

                val t1 = now()
                val node = createLibraryNode(library, raw, parent, analysisResult)
                tCreate += now() - t1

                val t2 = now()
                libraryNodeRepo.save(node)
                tSave += now() - t2

                saved++
            }
        }

        // Финальный лог
        val totalMs = (System.nanoTime() - startedNs) / 1_000_000
        val findMs = tFindExisting / 1_000_000
        val saveMs = tSave / 1_000_000
        val createMs = tCreate / 1_000_000

        val pctFind = findMs * 100.0 / totalMs
        val pctSave = saveMs * 100.0 / totalMs
        val pctCreate = createMs * 100.0 / totalMs

        log.info(
            "saveLibraryNodes: total=${totalMs}ms, saved=$saved | " +
                    "find=${findMs}ms (${String.format("%.1f", pctFind)}%), " +
                    "create=${createMs}ms (${String.format("%.1f", pctCreate)}%), " +
                    "save=${saveMs}ms (${String.format("%.1f", pctSave)}%)"
        )

        return saved
    }

    private fun createLibraryNode(
        library: Library,
        raw: RawLibraryNode,
        parent: LibraryNode?,
        analysisResult: com.bftcom.docgenerator.library.api.bytecode.BytecodeAnalysisResult?,
    ): LibraryNode {
        val metaMap = mutableMapOf<String, Any>(
            "annotations" to raw.annotations,
            "modifiers" to raw.modifiers.toList(),
        )
        metaMap.putAll(raw.meta)

        // Добавляем информацию об интеграционных вызовах (HTTP/Kafka/Camel) для методов
        if (raw.kind == NodeKind.METHOD && analysisResult != null) {
            val methodSummary = findMethodSummary(raw.fqn, analysisResult)
            if (methodSummary != null) {
                val integrationMeta = mutableMapOf<String, Any>()
                
                if (methodSummary.isParentClient) {
                    integrationMeta["isParentClient"] = true
                }
                
                // HTTP
                if (methodSummary.urls.isNotEmpty()) {
                    integrationMeta["urls"] = methodSummary.urls.toList()
                }
                if (methodSummary.httpMethods.isNotEmpty()) {
                    integrationMeta["httpMethods"] = methodSummary.httpMethods.toList()
                }
                if (methodSummary.hasRetry) {
                    integrationMeta["hasRetry"] = true
                }
                if (methodSummary.hasTimeout) {
                    integrationMeta["hasTimeout"] = true
                }
                if (methodSummary.hasCircuitBreaker) {
                    integrationMeta["hasCircuitBreaker"] = true
                }
                
                // Kafka
                if (methodSummary.kafkaTopics.isNotEmpty()) {
                    integrationMeta["kafkaTopics"] = methodSummary.kafkaTopics.toList()
                }
                if (methodSummary.directKafkaCalls.isNotEmpty()) {
                    integrationMeta["kafkaCalls"] = methodSummary.directKafkaCalls.map { call ->
                        mapOf(
                            "topic" to (call.topic ?: ""),
                            "operation" to call.operation,
                            "clientType" to call.clientType,
                        )
                    }
                }
                
                // Camel
                if (methodSummary.camelUris.isNotEmpty()) {
                    integrationMeta["camelUris"] = methodSummary.camelUris.toList()
                }
                if (methodSummary.directCamelCalls.isNotEmpty()) {
                    integrationMeta["camelCalls"] = methodSummary.directCamelCalls.map { call ->
                        mapOf(
                            "uri" to (call.uri ?: ""),
                            "endpointType" to (call.endpointType ?: ""),
                            "direction" to call.direction,
                        )
                    }
                }
                
                if (integrationMeta.isNotEmpty()) {
                    metaMap["integrationAnalysis"] = integrationMeta
                }
            }
        }

        return LibraryNode(
            library = library,
            fqn = raw.fqn,
            name = raw.name,
            packageName = raw.packageName,
            kind = raw.kind,
            lang = raw.lang,
            parent = parent,
            filePath = raw.filePath,
            lineStart = null,
            lineEnd = null,
            sourceCode = null,
            docComment = null,
            signature = raw.signature,
            meta = objectMapper.convertValue(metaMap, Map::class.java) as Map<String, Any>,
        )
    }

    /**
     * Находит сводку по методу по его FQN.
     * FQN метода имеет формат: com.example.Class.methodName
     */
    private fun findMethodSummary(
        methodFqn: String,
        analysisResult: com.bftcom.docgenerator.library.api.bytecode.BytecodeAnalysisResult,
    ): com.bftcom.docgenerator.library.api.bytecode.MethodSummary? {
        // Парсим FQN метода: com.example.Class.methodName -> owner=com.example.Class, name=methodName
        val lastDot = methodFqn.lastIndexOf('.')
        if (lastDot == -1) return null

        val ownerFqn = methodFqn.substring(0, lastDot)
        val methodName = methodFqn.substring(lastDot + 1)
        val owner = ownerFqn.replace('.', '/')

        // Ищем метод в сводках
        for ((methodId, summary) in analysisResult.methodSummaries) {
            if (methodId.ownerFqn == ownerFqn && methodId.name == methodName) {
                return summary
            }
        }

        return null
    }

    /**
     * Определяет, является ли класс значимым для интеграции между приложениями.
     */
    private fun isIntegrationRelevantClass(raw: RawLibraryNode): Boolean {
        // Только публичные классы/интерфейсы/енумы
        if ("public" !in raw.modifiers) {
            return false
        }

        // Классы с интеграционными аннотациями
        val hasIntegrationAnnotation = raw.annotations.any { ann ->
            ann.contains("RestController", ignoreCase = true) ||
            ann.contains("Controller", ignoreCase = true) ||
            ann.contains("FeignClient", ignoreCase = true) ||
            ann.contains("KafkaListener", ignoreCase = true) ||
            ann.contains("RabbitListener", ignoreCase = true) ||
            ann.contains("Service", ignoreCase = true) ||
            ann.contains("Component", ignoreCase = true) ||
            ann.contains("WebClient", ignoreCase = true)
        }
        if (hasIntegrationAnnotation) {
            return true
        }

        // HTTP клиенты
        val fqn = raw.fqn.lowercase()
        if (fqn.contains("webclient") ||
            fqn.contains("resttemplate") ||
            fqn.contains("okhttp") ||
            fqn.contains("httpclient") ||
            fqn.contains("feign") ||
            fqn.contains("restclient")) {
            return true
        }

        // Kafka клиенты
        if (fqn.contains("kafkaproducer") ||
            fqn.contains("kafkaconsumer") ||
            fqn.contains("kafka")) {
            return true
        }

        // Camel
        if (fqn.contains("routebuilder") ||
            fqn.contains("camel")) {
            return true
        }

        // Интерфейсы - могут быть контрактами интеграции
        if (raw.kind == NodeKind.INTERFACE) {
            return true
        }

        return false
    }

    /**
     * Определяет, является ли метод/поле значимым для интеграции.
     */
    private fun isIntegrationRelevantMember(
        raw: RawLibraryNode,
        relevantClassFqns: Set<String>,
        integrationMethods: Set<String>,
        analysisResult: com.bftcom.docgenerator.library.api.bytecode.BytecodeAnalysisResult?,
    ): Boolean {
        // Для методов
        if (raw.kind == NodeKind.METHOD) {
            // Публичные методы
            if ("public" !in raw.modifiers) {
                return false
            }

            // Методы с интеграционными аннотациями
            val hasIntegrationAnnotation = raw.annotations.any { ann ->
                ann.contains("GetMapping", ignoreCase = true) ||
                ann.contains("PostMapping", ignoreCase = true) ||
                ann.contains("PutMapping", ignoreCase = true) ||
                ann.contains("DeleteMapping", ignoreCase = true) ||
                ann.contains("PatchMapping", ignoreCase = true) ||
                ann.contains("RequestMapping", ignoreCase = true) ||
                ann.contains("KafkaListener", ignoreCase = true) ||
                ann.contains("RabbitListener", ignoreCase = true) ||
                ann.contains("Scheduled", ignoreCase = true) ||
                ann.contains("EventListener", ignoreCase = true)
            }
            if (hasIntegrationAnnotation) {
                return true
            }

            // Методы с интеграционными вызовами (HTTP/Kafka/Camel)
            if (raw.fqn in integrationMethods) {
                return true
            }

            // Публичные методы интеграционных классов
            val parentFqn = raw.parentFqn
            if (parentFqn != null && parentFqn in relevantClassFqns) {
                // Если родительский класс является интеграционным, сохраняем публичные методы
                return true
            }
        }

        // Для полей
        if (raw.kind == NodeKind.FIELD) {
            // Публичные поля интеграционных классов
            if ("public" in raw.modifiers) {
                val parentFqn = raw.parentFqn
                if (parentFqn != null && parentFqn in relevantClassFqns) {
                    // Сохраняем публичные поля интеграционных классов
                    return true
                }
            }

            // Статические константы интеграционных классов
            if ("static" in raw.modifiers && "final" in raw.modifiers) {
                val parentFqn = raw.parentFqn
                if (parentFqn != null && parentFqn in relevantClassFqns) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Определяет, является ли библиотека библиотекой компании.
     * Проверяет groupId на соответствие префиксам компании.
     */
    private fun isCompanyLibrary(coordinate: LibraryCoordinate): Boolean {
        val groupId = coordinate.groupId.lowercase()
        return whiteList.any { prefix ->
            groupId.startsWith(prefix.lowercase())
        }
    }

    private fun determineLibraryKind(coordinate: LibraryCoordinate): String {
        return when {
            coordinate.groupId.startsWith("org.springframework") -> "framework"
            coordinate.groupId.startsWith("com.fasterxml.jackson") -> "library"
            coordinate.groupId.startsWith("org.jetbrains.kotlin") -> "language"
            isCompanyLibrary(coordinate) -> "company"
            else -> "external"
        }
    }
}

