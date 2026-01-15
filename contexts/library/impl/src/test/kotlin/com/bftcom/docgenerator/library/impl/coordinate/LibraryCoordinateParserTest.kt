package com.bftcom.docgenerator.library.impl.coordinate

import com.bftcom.docgenerator.library.api.LibraryCoordinate
import com.bftcom.docgenerator.library.impl.bytecode.TestJarUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LibraryCoordinateParserTest {
    @Test
    fun `parseCoordinate - читает из pom_properties`(@TempDir dir: Path) {
        val jarPath = dir.resolve("lib.jar")
        val props =
            """
            groupId=com.bftcom
            artifactId=my-lib
            version=1.2.3
            """.trimIndent().toByteArray()

        val jar =
            TestJarUtils.writeJar(
                jarPath,
                mapOf(
                    "META-INF/maven/com.bftcom/my-lib/pom.properties" to props,
                ),
            )

        val c = LibraryCoordinateParser().parseCoordinate(jar)
        assertThat(c).isEqualTo(LibraryCoordinate("com.bftcom", "my-lib", "1.2.3"))
    }

    @Test
    fun `parseCoordinate - fallback по имени файла и пути`(@TempDir dir: Path) {
        // .../.m2/repository/com/bftcom/my-lib/1.2.3/my-lib-1.2.3.jar
        val jarPath =
            dir.resolve(".m2")
                .resolve("repository")
                .resolve("com")
                .resolve("bftcom")
                .resolve("my-lib")
                .resolve("1.2.3")
                .resolve("my-lib-1.2.3.jar")

        Files.createDirectories(jarPath.parent)
        val jar = TestJarUtils.emptyJar(jarPath)

        val c = LibraryCoordinateParser().parseCoordinate(jar)
        assertThat(c!!.groupId).isEqualTo("com.bftcom")
        assertThat(c.artifactId).isEqualTo("my-lib")
        assertThat(c.version).isEqualTo("1.2.3")
    }

    @Test
    fun `parseCoordinate - non-jar returns null`(@TempDir dir: Path) {
        val f = dir.resolve("x.txt").toFile().apply { writeText("x") }
        assertThat(LibraryCoordinateParser().parseCoordinate(f)).isNull()
    }
}

