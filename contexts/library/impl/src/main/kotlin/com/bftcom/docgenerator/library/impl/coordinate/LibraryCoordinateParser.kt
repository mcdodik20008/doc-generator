package com.bftcom.docgenerator.library.impl.coordinate

import com.bftcom.docgenerator.library.api.LibraryCoordinate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipEntry

/**
 * Парсер координат библиотеки из jar-файла.
 * Ищет координаты в META-INF/maven/<groupId>/<artifactId>/pom.properties
 * или в META-INF/MANIFEST.MF
 */
@Component
class LibraryCoordinateParser {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Извлекает координаты библиотеки из jar-файла.
     * @param jarFile Путь к jar-файлу
     * @return LibraryCoordinate или null, если не удалось определить
     */
    fun parseCoordinate(jarFile: File): LibraryCoordinate? {
        if (!jarFile.exists() || !jarFile.name.endsWith(".jar", ignoreCase = true)) {
            log.debug("Skipping non-jar file: {}", jarFile.name)
            return null
        }

        return try {
            JarFile(jarFile).use { jar ->
                // Пробуем найти в META-INF/maven/<groupId>/<artifactId>/pom.properties
                val pomProps = findPomProperties(jar)
                if (pomProps != null) {
                    log.trace("Found coordinates in pom.properties for jar: {}", jarFile.name)
                    return pomProps
                }

                // Пробуем извлечь из имени файла (fallback)
                val fromFileName = extractFromFileName(jarFile.name)
                if (fromFileName != null) {
                    log.trace("Extracted coordinates from filename for jar: {}", jarFile.name)
                    return fromFileName
                }

                log.debug("Could not determine coordinates for jar: {}", jarFile.name)
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to parse coordinates from jar {}: {}", jarFile.name, e.message)
            null
        }
    }

    private fun findPomProperties(jar: JarFile): LibraryCoordinate? {
        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("META-INF/maven/") && entry.name.endsWith("/pom.properties")) {
                return try {
                    jar.getInputStream(entry).use { input ->
                        val props = java.util.Properties()
                        props.load(input)
                        val groupId = props.getProperty("groupId") ?: return null
                        val artifactId = props.getProperty("artifactId") ?: return null
                        val version = props.getProperty("version") ?: return null
                        LibraryCoordinate(groupId, artifactId, version)
                    }
                } catch (e: Exception) {
                    log.debug("Failed to read pom.properties from {}: {}", entry.name, e.message)
                    null
                }
            }
        }
        return null
    }

    private fun extractFromFileName(fileName: String): LibraryCoordinate? {
        // Пробуем извлечь из имени файла вида: artifactId-version.jar
        // Это не очень надёжно, но лучше чем ничего
        val nameWithoutExt = fileName.removeSuffix(".jar").removeSuffix(".JAR")
        val parts = nameWithoutExt.split("-")
        if (parts.size >= 2) {
            // Последняя часть может быть версией
            val possibleVersion = parts.last()
            if (possibleVersion.matches(Regex("""\d+\.\d+.*"""))) {
                val artifactId = parts.dropLast(1).joinToString("-")
                // Для groupId используем "unknown" если не можем определить
                return LibraryCoordinate(
                    groupId = "unknown",
                    artifactId = artifactId,
                    version = possibleVersion,
                )
            }
        }
        return null
    }
}

