package com.bftcom.docgenerator.library.impl.coordinate

import com.bftcom.docgenerator.library.api.LibraryCoordinate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.util.Properties
import java.util.jar.JarFile

/**
 * Парсер координат библиотеки из jar-файла.
 *
 * Использует 9 стратегий (в порядке убывания надёжности):
 * 1. pom.properties в META-INF/maven/
 * 2. pom.xml в META-INF/maven/
 * 3. MANIFEST.MF (Implementation-*, Bundle-*, Automatic-Module-Name)
 * 4. Путь в кэше Gradle (.gradle/caches/modules-2/files-2.1/group/artifact/version/hash/)
 * 5. .pom файл рядом с JAR
 * 6. Gradle module metadata (.module файл рядом с JAR)
 * 7. Путь в Maven-репозитории (.m2/repository/group/path/artifact/version/)
 * 8. Gradle-проект (build/libs/) — чтение gradle.properties / build.gradle
 * 9. Сканирование пакетов внутри JAR (последний шанс)
 */
@Component
class LibraryCoordinateParser {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val VERSION_REGEX = Regex("""-(\d+(\.\d+).*)$""")

        private val REPO_STOP_WORDS = setOf(
            "repository", "libs", ".m2", ".gradle", "caches", "maven", "m2",
        )

        // .gradle/caches/modules-2/files-2.1/<group>/<artifact>/<version>/<hash>/<file>.jar
        private val GRADLE_CACHE_REGEX = Regex(
            """[/\\]\.gradle[/\\]caches[/\\]modules-\d+[/\\]files-[\d.]+[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\]([^/\\]+)[/\\][^/\\]+[/\\][^/\\]+\.jar$""",
            RegexOption.IGNORE_CASE,
        )
    }

    /**
     * Извлекает координаты библиотеки из jar-файла.
     * Перебирает 9 стратегий от самой надёжной до эвристической.
     *
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
                // === Метаданные внутри JAR (самые надёжные) ===

                // 1. pom.properties
                findPomProperties(jar)?.also {
                    log.trace("[pom.properties] {}", jarFile.name)
                    return it
                }

                // 2. pom.xml внутри JAR
                findPomXml(jar)?.also {
                    log.trace("[pom.xml inside] {}", jarFile.name)
                    return it
                }

                // 3. MANIFEST.MF
                findFromManifest(jar)?.also {
                    log.trace("[MANIFEST.MF] {}", jarFile.name)
                    return it
                }

                // === Внешние стратегии (путь, файлы рядом) ===

                // 4. Путь в кэше Gradle
                recoverFromGradleCachePath(jarFile)?.also {
                    log.trace("[Gradle cache path] {}", jarFile.name)
                    return it
                }

                // 5. .pom файл рядом с JAR
                findFromNearbyPom(jarFile)?.also {
                    log.trace("[nearby .pom] {}", jarFile.name)
                    return it
                }

                // 6. Gradle module metadata (.module)
                findFromGradleModuleMetadata(jarFile)?.also {
                    log.trace("[.module metadata] {}", jarFile.name)
                    return it
                }

                // 7. Maven repo path + имя файла
                recoverFromMavenRepoPath(jarFile)?.also {
                    log.trace("[Maven repo path] {}", jarFile.name)
                    return it
                }

                // 8. Gradle-проект (build/libs/) + gradle.properties
                recoverFromGradleProject(jarFile)?.also {
                    log.trace("[Gradle project] {}", jarFile.name)
                    return it
                }

                // 9. Сканирование пакетов (последний шанс)
                recoverFromPackageScan(jar, jarFile)?.also {
                    log.trace("[package scan] {}", jarFile.name)
                    return it
                }

                log.debug("Could not determine coordinates for jar: {}", jarFile.name)
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to parse coordinates from jar {}: {}", jarFile.name, e.message)
            null
        }
    }

    // ============================================================
    // Strategy 1: pom.properties в META-INF/maven/
    // ============================================================
    private fun findPomProperties(jar: JarFile): LibraryCoordinate? {
        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("META-INF/maven/") && entry.name.endsWith("/pom.properties")) {
                return try {
                    jar.getInputStream(entry).use { input ->
                        val props = Properties()
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

    // ============================================================
    // Strategy 2: pom.xml в META-INF/maven/
    // ============================================================
    private fun findPomXml(jar: JarFile): LibraryCoordinate? {
        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith("META-INF/maven/") && entry.name.endsWith("/pom.xml")) {
                return try {
                    jar.getInputStream(entry).use { input ->
                        val xml = input.bufferedReader().readText()
                        parsePomXmlCoordinate(xml)
                    }
                } catch (e: Exception) {
                    log.debug("Failed to read pom.xml from {}: {}", entry.name, e.message)
                    null
                }
            }
        }
        return null
    }

    // ============================================================
    // Strategy 3: MANIFEST.MF
    // ============================================================
    private fun findFromManifest(jar: JarFile): LibraryCoordinate? {
        val manifest = try {
            jar.manifest
        } catch (e: Exception) {
            return null
        } ?: return null

        val attrs = manifest.mainAttributes ?: return null

        // 3a. Implementation-Vendor-Id + Implementation-Title + Implementation-Version
        val implVendorId = attrs.getValue("Implementation-Vendor-Id")
        val implTitle = attrs.getValue("Implementation-Title")
        val implVersion = attrs.getValue("Implementation-Version")

        if (implVendorId != null && implTitle != null && implVersion != null) {
            return LibraryCoordinate(implVendorId, implTitle, implVersion)
        }

        // 3b. OSGi Bundle-SymbolicName + Bundle-Version
        val bundleName = attrs.getValue("Bundle-SymbolicName")
        val bundleVersion = attrs.getValue("Bundle-Version")

        if (bundleName != null && bundleVersion != null) {
            val cleanName = bundleName.split(";").first().trim()
            val coord = splitDottedName(cleanName, bundleVersion)
            if (coord != null) return coord
        }

        // 3c. Automatic-Module-Name + версия из Implementation-Version или Bundle-Version
        val moduleName = attrs.getValue("Automatic-Module-Name")
        val version = implVersion ?: bundleVersion
        if (moduleName != null && version != null) {
            val coord = splitDottedName(moduleName, version)
            if (coord != null) return coord
        }

        return null
    }

    /**
     * Разбивает точечное имя (Bundle-SymbolicName, Automatic-Module-Name)
     * на groupId и artifactId.
     *
     * Пример: "com.bftcom.mylib" → groupId="com.bftcom", artifactId="mylib"
     */
    private fun splitDottedName(dottedName: String, version: String): LibraryCoordinate? {
        val lastDot = dottedName.lastIndexOf('.')
        if (lastDot <= 0) return null

        val groupId = dottedName.substring(0, lastDot)
        val artifactId = dottedName.substring(lastDot + 1)

        // groupId должен содержать хотя бы 2 сегмента (com.example)
        if (!groupId.contains('.')) return null
        if (artifactId.isEmpty()) return null

        return LibraryCoordinate(groupId, artifactId, version)
    }

    // ============================================================
    // Strategy 4: Путь в кэше Gradle
    // ============================================================
    private fun recoverFromGradleCachePath(jarFile: File): LibraryCoordinate? {
        return try {
            val match = GRADLE_CACHE_REGEX.find(jarFile.absolutePath) ?: return null

            val groupId = match.groupValues[1]
            val artifactId = match.groupValues[2]
            val version = match.groupValues[3]

            LibraryCoordinate(groupId, artifactId, version)
        } catch (e: Exception) {
            null
        }
    }

    // ============================================================
    // Strategy 5: .pom файл рядом с JAR
    // ============================================================
    private fun findFromNearbyPom(jarFile: File): LibraryCoordinate? {
        try {
            val parent = jarFile.parentFile ?: return null
            val nameWithoutExt = jarFile.name.removeSuffix(".jar").removeSuffix(".JAR")

            // Ищем <имя-без-расширения>.pom (стандарт Maven-репозитория)
            val pomFile = File(parent, "$nameWithoutExt.pom")
            if (pomFile.exists() && pomFile.isFile) {
                val coord = parsePomXmlCoordinate(pomFile.readText(Charsets.UTF_8))
                if (coord != null) return coord
            }

            // Ищем единственный .pom файл в директории
            val pomFiles = parent.listFiles { f ->
                f.isFile && f.extension.equals("pom", ignoreCase = true)
            }
            if (pomFiles != null && pomFiles.size == 1) {
                val coord = parsePomXmlCoordinate(pomFiles[0].readText(Charsets.UTF_8))
                if (coord != null) return coord
            }
        } catch (e: Exception) {
            log.debug("Failed to read nearby pom for {}: {}", jarFile.name, e.message)
        }
        return null
    }

    // ============================================================
    // Strategy 6: Gradle module metadata (.module)
    // ============================================================
    private fun findFromGradleModuleMetadata(jarFile: File): LibraryCoordinate? {
        try {
            val parent = jarFile.parentFile ?: return null

            // Прямо рядом с JAR
            val moduleFiles = parent.listFiles { f ->
                f.isFile && f.extension.equals("module", ignoreCase = true)
            }
            if (moduleFiles != null) {
                for (mf in moduleFiles) {
                    val coord = parseGradleModuleFile(mf)
                    if (coord != null) return coord
                }
            }

            // В кэше Gradle .module лежит в соседней hash-директории:
            // <version>/<hash1>/file.jar   <version>/<hash2>/file.module
            val grandParent = parent.parentFile ?: return null
            val siblingDirs = grandParent.listFiles { f -> f.isDirectory } ?: return null
            for (siblingDir in siblingDirs) {
                if (siblingDir.absolutePath == parent.absolutePath) continue
                val mods = siblingDir.listFiles { f ->
                    f.isFile && f.extension.equals("module", ignoreCase = true)
                }
                if (mods != null) {
                    for (mf in mods) {
                        val coord = parseGradleModuleFile(mf)
                        if (coord != null) return coord
                    }
                }
            }
        } catch (e: Exception) {
            log.debug("Failed to read Gradle module metadata for {}: {}", jarFile.name, e.message)
        }
        return null
    }

    // ============================================================
    // Strategy 7: Maven repo directory structure + filename
    // ============================================================
    private fun recoverFromMavenRepoPath(jarFile: File): LibraryCoordinate? {
        val parts = parseFileName(jarFile) ?: return null
        val groupId = tryDetectGroupIdFromMavenPath(jarFile, parts.artifactId, parts.version)
            ?: return null

        return LibraryCoordinate(groupId, parts.artifactId, parts.version)
    }

    // ============================================================
    // Strategy 8: Gradle-проект (build/libs/) + gradle.properties
    // ============================================================
    private fun recoverFromGradleProject(jarFile: File): LibraryCoordinate? {
        try {
            val path = jarFile.absolutePath.replace('\\', '/')

            // Проверяем, что JAR лежит в build/libs/
            if (!path.contains("/build/libs/")) return null

            val parts = parseFileName(jarFile)
            val artifactId: String
            val version: String

            if (parts != null) {
                artifactId = parts.artifactId
                version = parts.version
            } else {
                artifactId = jarFile.name.removeSuffix(".jar").removeSuffix(".JAR")
                version = "unspecified"
            }

            // Поднимаемся из build/libs/ вверх по дереву проекта
            var dir = jarFile.parentFile?.parentFile?.parentFile
            val maxDepth = 10
            var depth = 0

            while (dir != null && depth < maxDepth) {
                val groupId = tryReadGroupFromDir(dir)
                if (groupId != null) {
                    return LibraryCoordinate(groupId, artifactId, version)
                }

                // Остановка на корне проекта (settings.gradle)
                if (File(dir, "settings.gradle.kts").exists() || File(dir, "settings.gradle").exists()) {
                    break
                }

                dir = dir.parentFile
                depth++
            }
        } catch (e: Exception) {
            log.debug("Failed to recover from Gradle project for {}: {}", jarFile.name, e.message)
        }
        return null
    }

    // ============================================================
    // Strategy 9: Сканирование пакетов внутри JAR
    // ============================================================
    private fun recoverFromPackageScan(jar: JarFile, jarFile: File): LibraryCoordinate? {
        try {
            val packages = mutableSetOf<String>()
            val entries = jar.entries()
            var scanned = 0
            val maxScan = 1000

            while (entries.hasMoreElements() && scanned < maxScan) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".class") && !entry.name.startsWith("META-INF/")) {
                    val lastSlash = entry.name.lastIndexOf('/')
                    if (lastSlash > 0) {
                        packages.add(entry.name.substring(0, lastSlash).replace('/', '.'))
                    }
                    scanned++
                }
            }

            if (packages.isEmpty()) return null

            val groupId = findCommonPackagePrefix(packages) ?: return null

            val parts = parseFileName(jarFile)
            val artifactId = parts?.artifactId
                ?: jarFile.name.removeSuffix(".jar").removeSuffix(".JAR")
            val version = parts?.version ?: "unknown"

            return LibraryCoordinate(groupId, artifactId, version)
        } catch (e: Exception) {
            log.debug("Failed to scan packages in {}: {}", jarFile.name, e.message)
            return null
        }
    }

    // ============================================================
    // Утилиты
    // ============================================================

    private data class FileNameParts(val artifactId: String, val version: String)

    /**
     * Парсит имя jar-файла: artifactId-version.jar
     */
    private fun parseFileName(jarFile: File): FileNameParts? {
        val nameWithoutExt = jarFile.name.removeSuffix(".jar").removeSuffix(".JAR")
        val versionMatch = VERSION_REGEX.find(nameWithoutExt) ?: return null
        val version = versionMatch.groupValues[1]
        val artifactId = nameWithoutExt.substring(0, versionMatch.range.first)
        if (artifactId.isEmpty()) return null
        return FileNameParts(artifactId, version)
    }

    /**
     * Парсит координаты из pom.xml (простой regex-парсинг без XML-библиотеки).
     * Поддерживает наследование groupId/version из <parent>.
     */
    private fun parsePomXmlCoordinate(xml: String): LibraryCoordinate? {
        val cleaned = xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

        // Выделяем блок <parent> и убираем его для поиска project-level значений
        val parentBlock = Regex("<parent>(.*?)</parent>", RegexOption.DOT_MATCHES_ALL)
            .find(cleaned)?.groupValues?.get(1)
        val withoutParent = if (parentBlock != null) {
            cleaned.replace(Regex("<parent>.*?</parent>", RegexOption.DOT_MATCHES_ALL), "")
        } else {
            cleaned
        }

        // groupId: сначала project-level, затем из parent
        val groupId = extractXmlTag(withoutParent, "groupId")
            ?: parentBlock?.let { extractXmlTag(it, "groupId") }
            ?: return null

        val artifactId = extractXmlTag(withoutParent, "artifactId") ?: return null

        // version: сначала project-level, затем из parent
        val version = extractXmlTag(withoutParent, "version")
            ?: parentBlock?.let { extractXmlTag(it, "version") }
            ?: return null

        return LibraryCoordinate(groupId, artifactId, version)
    }

    private fun extractXmlTag(xml: String, tag: String): String? {
        val match = Regex("<$tag>\\s*(.*?)\\s*</$tag>").find(xml) ?: return null
        val value = match.groupValues[1].trim()
        // Пропускаем Maven property references типа ${project.groupId}
        if (value.startsWith("\${")) return null
        return value.takeIf { it.isNotEmpty() }
    }

    /**
     * Парсит Gradle module metadata файл (.module) — JSON-формат.
     * Ищет координаты в блоке "component": { "group": "...", "module": "...", "version": "..." }
     */
    private fun parseGradleModuleFile(moduleFile: File): LibraryCoordinate? {
        return try {
            val json = moduleFile.readText(Charsets.UTF_8)
            val componentMatch = Regex(""""component"\s*:\s*\{([^}]+)\}""").find(json) ?: return null
            val component = componentMatch.groupValues[1]

            val group = extractJsonStringValue(component, "group") ?: return null
            val module = extractJsonStringValue(component, "module") ?: return null
            val version = extractJsonStringValue(component, "version") ?: return null

            LibraryCoordinate(group, module, version)
        } catch (e: Exception) {
            log.debug("Failed to parse Gradle module file {}: {}", moduleFile.name, e.message)
            null
        }
    }

    private fun extractJsonStringValue(json: String, key: String): String? {
        val regex = Regex(""""$key"\s*:\s*"([^"]+)"""")
        return regex.find(json)?.groupValues?.get(1)
    }

    /**
     * Определяет groupId из стандартной Maven-структуры пути:
     * .../group/id/parts/artifactId/version/file.jar
     */
    private fun tryDetectGroupIdFromMavenPath(
        jarFile: File,
        artifactId: String,
        version: String,
    ): String? {
        try {
            val versionDir = jarFile.parentFile ?: return null
            if (versionDir.name != version) return null

            val artifactDir = versionDir.parentFile ?: return null
            if (artifactDir.name != artifactId) return null

            val groupParts = mutableListOf<String>()
            var currentDir = artifactDir.parentFile

            while (currentDir != null && currentDir.name.isNotEmpty()) {
                if (REPO_STOP_WORDS.contains(currentDir.name) || currentDir.name.startsWith(".")) {
                    break
                }
                groupParts.add(0, currentDir.name)
                currentDir = currentDir.parentFile
            }

            if (groupParts.isNotEmpty()) {
                return groupParts.joinToString(".")
            }
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * Ищет group в gradle.properties или build.gradle(.kts) в указанной директории.
     */
    private fun tryReadGroupFromDir(dir: File): String? {
        // gradle.properties
        val gradleProps = File(dir, "gradle.properties")
        if (gradleProps.exists() && gradleProps.isFile) {
            try {
                val props = Properties()
                gradleProps.inputStream().use { props.load(it) }
                val group = props.getProperty("group")
                if (!group.isNullOrBlank()) return group
            } catch (_: Exception) {
            }
        }

        // build.gradle.kts
        val buildKts = File(dir, "build.gradle.kts")
        if (buildKts.exists() && buildKts.isFile) {
            val group = extractGroupFromBuildGradle(buildKts)
            if (group != null) return group
        }

        // build.gradle (Groovy)
        val buildGroovy = File(dir, "build.gradle")
        if (buildGroovy.exists() && buildGroovy.isFile) {
            val group = extractGroupFromBuildGradle(buildGroovy)
            if (group != null) return group
        }

        return null
    }

    private fun extractGroupFromBuildGradle(file: File): String? {
        return try {
            val text = file.readText(Charsets.UTF_8)
            // group = "com.bftcom" или group = 'com.bftcom' или group "com.bftcom"
            val match = Regex("""group\s*=?\s*["']([^"']+)["']""").find(text)
            match?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Находит общий префикс пакетов (минимум 2 сегмента).
     * Например: {com.bftcom.mylib.service, com.bftcom.mylib.model} → "com.bftcom.mylib"
     */
    private fun findCommonPackagePrefix(packages: Set<String>): String? {
        if (packages.isEmpty()) return null

        val splitPackages = packages.map { it.split(".") }
        val minLength = splitPackages.minOf { it.size }

        val commonParts = mutableListOf<String>()
        for (i in 0 until minLength) {
            val segment = splitPackages[0][i]
            if (splitPackages.all { it[i] == segment }) {
                commonParts.add(segment)
            } else {
                break
            }
        }

        return if (commonParts.size >= 2) {
            commonParts.joinToString(".")
        } else {
            null
        }
    }
}
