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
                val fromFileName = recoverFromPathAndName(jarFile)
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

    private fun recoverFromPathAndName(jarFile: File): LibraryCoordinate? {
        val fileName = jarFile.name

        // 1. Парсим имя файла: artifactId-version.jar
        val nameWithoutExt = fileName.removeSuffix(".jar").removeSuffix(".JAR")

        // Ищем версию (цифры с точками в конце)
        // Регулярка ищет дефис, после которого идут цифры (версия)
        // Например: my-lib-1.0.2.jar -> artifact: my-lib, version: 1.0.2
        val versionMatch = Regex("""-(\d+(\.\d+).*)$""").find(nameWithoutExt) ?: return null

        val version = versionMatch.groupValues[1] // "1.0.2"
        val artifactId = nameWithoutExt.substring(0, versionMatch.range.first) // "my-lib"

        // 2. Теперь самое интересное: пытаемся восстановить GroupId из пути
        val groupId =
            tryDetectGroupIdFromPath(jarFile, artifactId, version)
                ?: "unknown" // Если путь не стандартный, все-таки придется unknown

        return LibraryCoordinate(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
        )
    }

    private fun tryDetectGroupIdFromPath(
        jarFile: File,
        artifactId: String,
        version: String,
    ): String? {
        try {
            // Стандартная структура Maven: .../group/id/parts/artifactId/version/file.jar
            // Родитель файла должен быть версией
            val versionDir = jarFile.parentFile ?: return null
            if (versionDir.name != version) return null

            // Родитель версии должен быть артефактом
            val artifactDir = versionDir.parentFile ?: return null
            if (artifactDir.name != artifactId) return null

            // Всё, что выше artifactDir — это части groupId.
            // Но нам нужно знать, где остановиться.
            // Обычно останавливаются на папках типа "repository", "m2", ".gradle", "libs" или корне диска.

            val groupParts = mutableListOf<String>()
            var currentDir = artifactDir.parentFile

            // Список слов-маркеров, на которых надо остановиться (корни репозиториев)
            val stopWords = setOf("repository", "libs", ".m2", ".gradle", "caches", "maven", "m2")

            while (currentDir != null && currentDir.name.isNotEmpty()) {
                if (stopWords.contains(currentDir.name) || currentDir.name.startsWith(".")) {
                    break
                }
                // Добавляем часть пути в начало списка
                groupParts.add(0, currentDir.name)
                currentDir = currentDir.parentFile
            }

            if (groupParts.isNotEmpty()) {
                return groupParts.joinToString(".")
            }
        } catch (e: Exception) {
            // Игнорируем ошибки доступа к ФС
        }
        return null
    }
}
