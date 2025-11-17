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
import com.bftcom.docgenerator.library.impl.coordinate.LibraryCoordinateParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.OffsetDateTime

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
) : LibraryBuilder {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun buildLibraries(classpath: List<File>): LibraryBuildResult {
        log.info("Starting library build for {} classpath entries", classpath.size)

        var librariesProcessed = 0
        var librariesSkipped = 0
        var nodesCreated = 0
        val errors = mutableListOf<String>()

        // Фильтруем только jar-файлы
        val jarFiles = classpath.filter { it.name.endsWith(".jar", ignoreCase = true) }
        log.debug("Found {} jar files in classpath", jarFiles.size)

        for (jarFile in jarFiles) {
            try {
                // 1. Извлекаем координаты
                val coordinate = coordinateParser.parseCoordinate(jarFile)
                if (coordinate == null) {
                    log.debug("Skipping jar without coordinates: {}", jarFile.name)
                    continue
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
                    // Можно проверить, нужно ли обновить ноды (если версия изменилась)
                    continue
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

    private fun saveLibraryNodes(
        library: Library,
        rawNodes: List<com.bftcom.docgenerator.library.api.RawLibraryNode>,
    ): Int {
        var saved = 0
        val parentMap = mutableMapOf<String, LibraryNode>()

        // Сначала создаём классы (чтобы потом можно было ссылаться на них как на parent)
        val classNodes = rawNodes.filter { it.kind in setOf(NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.ENUM) }
        for (raw in classNodes) {
            val existing = libraryNodeRepo.findByLibraryIdAndFqn(library.id!!, raw.fqn)
            if (existing == null) {
                val node = createLibraryNode(library, raw, null)
                val savedNode = libraryNodeRepo.save(node)
                parentMap[raw.fqn] = savedNode
                saved++
            } else {
                parentMap[raw.fqn] = existing
            }
        }

        // Затем создаём методы и поля
        val memberNodes = rawNodes.filter { it.kind in setOf(NodeKind.METHOD, NodeKind.FIELD) }
        for (raw in memberNodes) {
            val existing = libraryNodeRepo.findByLibraryIdAndFqn(library.id!!, raw.fqn)
            if (existing == null) {
                val parent = raw.parentFqn?.let { parentMap[it] }
                val node = createLibraryNode(library, raw, parent)
                libraryNodeRepo.save(node)
                saved++
            }
        }

        return saved
    }

    private fun createLibraryNode(
        library: Library,
        raw: com.bftcom.docgenerator.library.api.RawLibraryNode,
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

