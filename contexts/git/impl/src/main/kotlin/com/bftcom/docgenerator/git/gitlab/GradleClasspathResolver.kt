package com.bftcom.docgenerator.git.gitlab

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@Component
class GradleClasspathResolver {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Создает временный init-скрипт на Groovy (.gradle),
     * который поймет любая версия Gradle.
     */
    private fun createClasspathInitScript(): Path {
        val scriptContents =
            """
            // Уникальный префикс, чтобы мы могли найти нужные строки в выхлопе
            def CLASSPATH_MARKER = "CLASSPATH_ENTRY:"

            allprojects {
                // 'afterEvaluate' принимает 'project' как параметр в Groovy
                afterEvaluate { project ->
                    try {
                        // Проверяем, есть ли Java/Kotlin плагины
                        if (project.plugins.hasPlugin("java") || project.plugins.hasPlugin("java-library") || project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
                            
                            // 'def' вместо 'val'
                            def runtimeClasspath = project.configurations.getByName("runtimeClasspath")
                            
                            // '.each' вместо '.forEach'
                            runtimeClasspath.files.each { file ->
                                println(CLASSPATH_MARKER + file.absolutePath)
                            }
                        }
                    } catch (Exception e) {
                        // Игнорируем, если конфигурации 'runtimeClasspath' нет
                        log.debug("Failed to access runtimeClasspath configuration: {}", e.message)
                    }
                }
            }
            """.trimIndent()

        // Создаем временный файл, но теперь с расширением .gradle
        val tempScript = Files.createTempFile("gradle-init-", ".gradle") // <-- ИЗМЕНИЛИ .kts на .gradle
        Files.writeString(tempScript, scriptContents)
        tempScript.toFile().deleteOnExit() // Убираем за собой
        return tempScript
    }

    /**
     * Запускает Gradle с init-скриптом и парсит выхлоп,
     * возвращая список файлов classpath.
     */
    fun resolveClasspath(projectDir: Path): List<File> {
        val initScriptPath =
            try {
                createClasspathInitScript()
            } catch (e: Exception) {
                log.error("Failed to create Gradle init script", e)
                return emptyList()
            }

        val os = System.getProperty("os.name").lowercase()
        val isWindows = os.contains("win")
        val gradlew = if (isWindows) "gradlew.bat" else "gradlew"
        val gradlewPath = projectDir.resolve(gradlew)

        if (!gradlewPath.exists()) {
            log.warn("Cannot find gradlew at [$gradlewPath]. Skipping classpath resolution.")
            return emptyList()
        }

        // Мы запускаем безвредную задачу 'tasks' (с --quiet), которая заставит
        // Gradle выполнить наш init-скрипт
        val command =
            if (isWindows) {
                listOf("cmd.exe", "/c", gradlewPath.toString(), "--init-script", initScriptPath.toString(), "tasks", "--quiet")
            } else {
                listOf("/bin/sh", gradlewPath.toString(), "--init-script", initScriptPath.toString(), "tasks", "--quiet")
            }

        log.info("Fetching classpath from [$projectDir] using init script...")
        val classpathFiles = mutableListOf<File>()

        try {
            val process =
                ProcessBuilder(command)
                    .directory(projectDir.toFile())
                    .redirectErrorStream(true)
                    .start()

            val reader = process.inputStream.bufferedReader()
            var line: String?

            log.warn("--- [Gradle Init] STDOUT/STDERR for ${projectDir.fileName} ---")

            while (reader.readLine().also { line = it } != null) {
                // Ищем наш маркер!
                if (line?.startsWith("CLASSPATH_ENTRY:") == true) {
                    val filePath = line.substringAfter("CLASSPATH_ENTRY:")
                    val file = File(filePath)
                    if (file.exists()) {
                        classpathFiles.add(file)
                    }
                } else {
                    log.debug("[Gradle RAW]: $line")
                }
            }

            log.warn("--- [Gradle Init] END STDOUT/STDERR ---")

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                log.warn("Gradle init script task failed with exit code $exitCode. Classpath may be incomplete.")
            }
        } catch (e: Exception) {
            log.error("Failed to run Gradle init script in [$projectDir]", e)
        }

        log.info("Found ${classpathFiles.size} classpath entries via init script.")
        return classpathFiles.distinct()
    }
}
