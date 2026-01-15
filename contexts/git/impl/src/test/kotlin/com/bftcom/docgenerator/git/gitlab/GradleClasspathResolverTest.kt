package com.bftcom.docgenerator.git.gitlab

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GradleClasspathResolverTest {
    @Test
    fun `resolveClasspath - возвращает empty если gradlew не найден`(@TempDir dir: Path) {
        val resolver = GradleClasspathResolver()
        assertThat(resolver.resolveClasspath(dir)).isEmpty()
    }

    @Test
    fun `resolveClasspath - парсит CLASSPATH_ENTRY из gradlew bat`(@TempDir dir: Path) {
        // создаём gradlew.bat (на Windows это и будет выбранный файл)
        val gradlew = dir.resolve("gradlew.bat")

        val jar1 = dir.resolve("a.jar")
        val jar2 = dir.resolve("b.jar")
        Files.write(jar1, byteArrayOf())
        Files.write(jar2, byteArrayOf())

        // Скрипт игнорирует аргументы и печатает маркеры
        Files.writeString(
            gradlew,
            """
            @echo off
            echo CLASSPATH_ENTRY:${jar1.toAbsolutePath()}
            echo CLASSPATH_ENTRY:${jar2.toAbsolutePath()}
            exit /b 0
            """.trimIndent(),
        )

        val resolver = GradleClasspathResolver()
        val cp = resolver.resolveClasspath(dir)

        assertThat(cp.map { it.absolutePath }).containsExactlyInAnyOrder(
            jar1.toFile().absolutePath,
            jar2.toFile().absolutePath,
        )
    }
}

