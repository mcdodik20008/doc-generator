package com.bftcom.docgenerator.library.impl

import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.db.LibraryRepository
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.library.Library
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.library.api.BytecodeParser
import com.bftcom.docgenerator.library.api.LibraryBuilder
import com.bftcom.docgenerator.library.api.LibraryBuildResult
import com.bftcom.docgenerator.library.api.LibraryCoordinate
import com.bftcom.docgenerator.library.api.RawLibraryNode
import com.bftcom.docgenerator.library.impl.coordinate.LibraryCoordinateParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.OffsetDateTime
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
    private val libraryRepo: LibraryRepository,
    private val libraryNodeRepo: LibraryNodeRepository,
    private val objectMapper: ObjectMapper,
    @Lazy private val self: LibraryBuilderImpl?, // важно для вызова @Transactional-метода
) : LibraryBuilder {
    private val log = LoggerFactory.getLogger(javaClass)

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

        // 4. Сохраняем LibraryNode
        val savedNodes = saveLibraryNodes(library, rawNodes)
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
    ): Int {
        val startedNs = System.nanoTime()

        var saved = 0
        val parentMap = mutableMapOf<String, LibraryNode>()

        var tFindExisting = 0L
        var tSave = 0L
        var tCreate = 0L

        fun now() = System.nanoTime()

        // Сначала классы
        val classNodes = rawNodes.filter { it.kind in setOf(NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.ENUM) }
        for (raw in classNodes) {

            val t0 = now()
            val existing = libraryNodeRepo.findByLibraryIdAndFqn(library.id!!, raw.fqn)
            tFindExisting += now() - t0

            if (existing == null) {
                val t1 = now()
                val node = createLibraryNode(library, raw, null)
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
        val memberNodes = rawNodes.filter { it.kind in setOf(NodeKind.METHOD, NodeKind.FIELD) }
        for (raw in memberNodes) {

            val t0 = now()
            val existing = libraryNodeRepo.findByLibraryIdAndFqn(library.id!!, raw.fqn)
            tFindExisting += now() - t0

            if (existing == null) {
                val parent = raw.parentFqn?.let { parentMap[it] }

                val t1 = now()
                val node = createLibraryNode(library, raw, parent)
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
    ): LibraryNode {
        val metaMap = mapOf(
            "annotations" to raw.annotations,
            "modifiers" to raw.modifiers.toList(),
        ) + raw.meta

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

    private fun determineLibraryKind(coordinate: LibraryCoordinate): String? {
        return when {
            coordinate.groupId.startsWith("org.springframework") -> "framework"
            coordinate.groupId.startsWith("com.fasterxml.jackson") -> "library"
            coordinate.groupId.startsWith("org.jetbrains.kotlin") -> "language"
            else -> "external"
        }
    }
}

