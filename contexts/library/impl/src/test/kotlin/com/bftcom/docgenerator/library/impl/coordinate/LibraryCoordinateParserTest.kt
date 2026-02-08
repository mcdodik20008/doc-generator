package com.bftcom.docgenerator.library.impl.coordinate

import com.bftcom.docgenerator.library.api.LibraryCoordinate
import com.bftcom.docgenerator.library.impl.bytecode.TestJarUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes

class LibraryCoordinateParserTest {

    private val parser = LibraryCoordinateParser()

    // ============================================================
    // Strategy 1: pom.properties
    // ============================================================
    @Nested
    inner class PomPropertiesStrategy {

        @Test
        fun `читает координаты из pom_properties`(@TempDir dir: Path) {
            val jarPath = dir.resolve("lib.jar")
            val props = """
                groupId=com.bftcom
                artifactId=my-lib
                version=1.2.3
            """.trimIndent().toByteArray()

            val jar = TestJarUtils.writeJar(
                jarPath,
                mapOf("META-INF/maven/com.bftcom/my-lib/pom.properties" to props),
            )

            val c = parser.parseCoordinate(jar)
            assertThat(c).isEqualTo(LibraryCoordinate("com.bftcom", "my-lib", "1.2.3"))
        }

        @Test
        fun `pom_properties имеет приоритет над другими стратегиями`(@TempDir dir: Path) {
            val pomPropsPath = dir.resolve(".m2")
                .resolve("repository").resolve("wrong").resolve("group")
                .resolve("other-lib").resolve("9.9.9")
                .resolve("other-lib-9.9.9.jar")

            Files.createDirectories(pomPropsPath.parent)
            val props = """
                groupId=com.bftcom
                artifactId=my-lib
                version=1.2.3
            """.trimIndent().toByteArray()

            val jar = TestJarUtils.writeJar(
                pomPropsPath,
                mapOf("META-INF/maven/com.bftcom/my-lib/pom.properties" to props),
            )

            val c = parser.parseCoordinate(jar)
            // pom.properties должен победить, а не Maven-путь
            assertThat(c).isEqualTo(LibraryCoordinate("com.bftcom", "my-lib", "1.2.3"))
        }
    }

    // ============================================================
    // Strategy 2: pom.xml inside JAR
    // ============================================================
    @Nested
    inner class PomXmlInsideJarStrategy {

        @Test
        fun `читает координаты из pom_xml внутри JAR`(@TempDir dir: Path) {
            val pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <groupId>com.bftcom</groupId>
                    <artifactId>core-lib</artifactId>
                    <version>2.0.0</version>
                </project>
            """.trimIndent().toByteArray()

            val jarPath = dir.resolve("core-lib.jar")
            val jar = TestJarUtils.writeJar(
                jarPath,
                mapOf("META-INF/maven/com.bftcom/core-lib/pom.xml" to pomXml),
            )

            val c = parser.parseCoordinate(jar)
            assertThat(c).isEqualTo(LibraryCoordinate("com.bftcom", "core-lib", "2.0.0"))
        }

        @Test
        fun `наследует groupId из parent в pom_xml`(@TempDir dir: Path) {
            val pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <parent>
                        <groupId>com.bftcom</groupId>
                        <artifactId>parent-pom</artifactId>
                        <version>1.0.0</version>
                    </parent>
                    <artifactId>child-lib</artifactId>
                </project>
            """.trimIndent().toByteArray()

            val jarPath = dir.resolve("child-lib.jar")
            val jar = TestJarUtils.writeJar(
                jarPath,
                mapOf("META-INF/maven/com.bftcom/child-lib/pom.xml" to pomXml),
            )

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom")
            assertThat(c.artifactId).isEqualTo("child-lib")
            assertThat(c.version).isEqualTo("1.0.0") // inherited from parent
        }

        @Test
        fun `пропускает Maven property references в pom_xml`(@TempDir dir: Path) {
            val pomXml = """
                <project>
                    <groupId>${"$"}{project.groupId}</groupId>
                    <artifactId>some-lib</artifactId>
                    <version>1.0</version>
                </project>
            """.trimIndent().toByteArray()

            val jarPath = dir.resolve("some-lib.jar")
            val jar = TestJarUtils.writeJar(
                jarPath,
                mapOf("META-INF/maven/x/some-lib/pom.xml" to pomXml),
            )

            // groupId is a property ref, no parent → should return null from pom.xml strategy
            val c = parser.parseCoordinate(jar)
            // Может быть null или определён другой стратегией
            if (c != null) {
                assertThat(c.groupId).doesNotStartWith("\${")
            }
        }

        @Test
        fun `pom_xml с комментариями`(@TempDir dir: Path) {
            val pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- This is a comment -->
                <project>
                    <groupId>ru.bftcom</groupId>
                    <!-- <artifactId>wrong</artifactId> -->
                    <artifactId>correct-lib</artifactId>
                    <version>3.1.0</version>
                </project>
            """.trimIndent().toByteArray()

            val jarPath = dir.resolve("correct-lib.jar")
            val jar = TestJarUtils.writeJar(
                jarPath,
                mapOf("META-INF/maven/ru.bftcom/correct-lib/pom.xml" to pomXml),
            )

            val c = parser.parseCoordinate(jar)
            assertThat(c).isEqualTo(LibraryCoordinate("ru.bftcom", "correct-lib", "3.1.0"))
        }
    }

    // ============================================================
    // Strategy 3: MANIFEST.MF
    // ============================================================
    @Nested
    inner class ManifestStrategy {

        @Test
        fun `Implementation-Vendor-Id + Title + Version`(@TempDir dir: Path) {
            val jarPath = dir.resolve("impl-lib.jar")
            val jar = writeJarWithManifest(jarPath) { attrs ->
                attrs.putValue("Implementation-Vendor-Id", "com.bftcom")
                attrs.putValue("Implementation-Title", "impl-lib")
                attrs.putValue("Implementation-Version", "4.0.0")
            }

            val c = parser.parseCoordinate(jar)
            assertThat(c).isEqualTo(LibraryCoordinate("com.bftcom", "impl-lib", "4.0.0"))
        }

        @Test
        fun `Bundle-SymbolicName + Bundle-Version (OSGi)`(@TempDir dir: Path) {
            val jarPath = dir.resolve("osgi-lib.jar")
            val jar = writeJarWithManifest(jarPath) { attrs ->
                attrs.putValue("Bundle-SymbolicName", "com.bftcom.mylib")
                attrs.putValue("Bundle-Version", "2.1.0")
            }

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom")
            assertThat(c.artifactId).isEqualTo("mylib")
            assertThat(c.version).isEqualTo("2.1.0")
        }

        @Test
        fun `Bundle-SymbolicName с директивами (singleton)`(@TempDir dir: Path) {
            val jarPath = dir.resolve("osgi-singleton.jar")
            val jar = writeJarWithManifest(jarPath) { attrs ->
                attrs.putValue("Bundle-SymbolicName", "com.bftcom.core;singleton:=true")
                attrs.putValue("Bundle-Version", "1.0.0")
            }

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom")
            assertThat(c.artifactId).isEqualTo("core")
        }

        @Test
        fun `Automatic-Module-Name + Implementation-Version`(@TempDir dir: Path) {
            val jarPath = dir.resolve("module-lib.jar")
            val jar = writeJarWithManifest(jarPath) { attrs ->
                attrs.putValue("Automatic-Module-Name", "com.bftcom.utils")
                attrs.putValue("Implementation-Version", "5.0.0")
            }

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom")
            assertThat(c.artifactId).isEqualTo("utils")
            assertThat(c.version).isEqualTo("5.0.0")
        }

        @Test
        fun `не распознаёт слишком короткое имя (менее 3 сегментов)`(@TempDir dir: Path) {
            val jarPath = dir.resolve("short.jar")
            val jar = writeJarWithManifest(jarPath) { attrs ->
                attrs.putValue("Bundle-SymbolicName", "mylib")
                attrs.putValue("Bundle-Version", "1.0.0")
            }

            val c = parser.parseCoordinate(jar)
            // "mylib" не может быть разбит на groupId.artifactId → null от MANIFEST стратегии
            // Другие стратегии тоже не смогут (пустой JAR, нет пути)
            assertThat(c).isNull()
        }
    }

    // ============================================================
    // Strategy 4: Gradle cache path
    // ============================================================
    @Nested
    inner class GradleCachePathStrategy {

        @Test
        fun `распознаёт путь в кэше Gradle`(@TempDir dir: Path) {
            // .gradle/caches/modules-2/files-2.1/com.bftcom/my-lib/1.0.0/abc123/my-lib-1.0.0.jar
            val jarPath = dir.resolve(".gradle")
                .resolve("caches").resolve("modules-2").resolve("files-2.1")
                .resolve("com.bftcom").resolve("my-lib").resolve("1.0.0")
                .resolve("abc123def")
                .resolve("my-lib-1.0.0.jar")

            Files.createDirectories(jarPath.parent)
            val jar = TestJarUtils.emptyJar(jarPath)

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom")
            assertThat(c.artifactId).isEqualTo("my-lib")
            assertThat(c.version).isEqualTo("1.0.0")
        }

        @Test
        fun `распознаёт Gradle cache с многосегментным groupId`(@TempDir dir: Path) {
            val jarPath = dir.resolve(".gradle")
                .resolve("caches").resolve("modules-2").resolve("files-2.1")
                .resolve("org.springframework.boot").resolve("spring-boot-starter").resolve("3.2.0")
                .resolve("hash1234")
                .resolve("spring-boot-starter-3.2.0.jar")

            Files.createDirectories(jarPath.parent)
            val jar = TestJarUtils.emptyJar(jarPath)

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("org.springframework.boot")
            assertThat(c.artifactId).isEqualTo("spring-boot-starter")
            assertThat(c.version).isEqualTo("3.2.0")
        }
    }

    // ============================================================
    // Strategy 5: .pom near JAR
    // ============================================================
    @Nested
    inner class NearbyPomStrategy {

        @Test
        fun `читает pom файл рядом с JAR`(@TempDir dir: Path) {
            val jarDir = dir.resolve("repo")
            Files.createDirectories(jarDir)

            // JAR без метаданных
            val jar = TestJarUtils.emptyJar(jarDir.resolve("my-lib-1.0.0.jar"))

            // .pom файл рядом
            val pomContent = """
                <project>
                    <groupId>com.bftcom</groupId>
                    <artifactId>my-lib</artifactId>
                    <version>1.0.0</version>
                </project>
            """.trimIndent()
            Files.writeString(jarDir.resolve("my-lib-1.0.0.pom"), pomContent)

            val c = parser.parseCoordinate(jar)
            assertThat(c).isEqualTo(LibraryCoordinate("com.bftcom", "my-lib", "1.0.0"))
        }

        @Test
        fun `читает единственный pom в директории`(@TempDir dir: Path) {
            val jarDir = dir.resolve("repo")
            Files.createDirectories(jarDir)

            val jar = TestJarUtils.emptyJar(jarDir.resolve("custom-name.jar"))

            // .pom с другим именем, но единственный в директории
            val pomContent = """
                <project>
                    <groupId>ru.bftcom</groupId>
                    <artifactId>custom</artifactId>
                    <version>2.0.0</version>
                </project>
            """.trimIndent()
            Files.writeString(jarDir.resolve("artifact.pom"), pomContent)

            val c = parser.parseCoordinate(jar)
            assertThat(c).isEqualTo(LibraryCoordinate("ru.bftcom", "custom", "2.0.0"))
        }
    }

    // ============================================================
    // Strategy 6: Gradle module metadata (.module)
    // ============================================================
    @Nested
    inner class GradleModuleMetadataStrategy {

        @Test
        fun `читает module файл рядом с JAR`(@TempDir dir: Path) {
            val jarDir = dir.resolve("modules")
            Files.createDirectories(jarDir)

            val jar = TestJarUtils.emptyJar(jarDir.resolve("my-lib-1.0.0.jar"))

            val moduleJson = """
                {
                  "formatVersion": "1.1",
                  "component": {
                    "group": "com.bftcom",
                    "module": "my-lib",
                    "version": "1.0.0"
                  },
                  "variants": []
                }
            """.trimIndent()
            Files.writeString(jarDir.resolve("my-lib-1.0.0.module"), moduleJson)

            val c = parser.parseCoordinate(jar)
            assertThat(c).isEqualTo(LibraryCoordinate("com.bftcom", "my-lib", "1.0.0"))
        }

        @Test
        fun `ищет module в соседней hash-директории (Gradle cache)`(@TempDir dir: Path) {
            // Структура Gradle cache:
            // <version>/hash1/my-lib-1.0.0.jar
            // <version>/hash2/my-lib-1.0.0.module
            val versionDir = dir.resolve("version-root")
            val hash1Dir = versionDir.resolve("hash1")
            val hash2Dir = versionDir.resolve("hash2")
            Files.createDirectories(hash1Dir)
            Files.createDirectories(hash2Dir)

            val jar = TestJarUtils.emptyJar(hash1Dir.resolve("my-lib-1.0.0.jar"))

            val moduleJson = """
                {
                  "formatVersion": "1.1",
                  "component": {
                    "group": "com.bftcom.ext",
                    "module": "ext-lib",
                    "version": "3.0.0"
                  }
                }
            """.trimIndent()
            Files.writeString(hash2Dir.resolve("ext-lib-3.0.0.module"), moduleJson)

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom.ext")
            assertThat(c.artifactId).isEqualTo("ext-lib")
            assertThat(c.version).isEqualTo("3.0.0")
        }
    }

    // ============================================================
    // Strategy 7: Maven repo path
    // ============================================================
    @Nested
    inner class MavenRepoPathStrategy {

        @Test
        fun `fallback по имени файла и Maven-пути`(@TempDir dir: Path) {
            // .m2/repository/com/bftcom/my-lib/1.2.3/my-lib-1.2.3.jar
            val jarPath = dir.resolve(".m2")
                .resolve("repository").resolve("com").resolve("bftcom")
                .resolve("my-lib").resolve("1.2.3")
                .resolve("my-lib-1.2.3.jar")

            Files.createDirectories(jarPath.parent)
            val jar = TestJarUtils.emptyJar(jarPath)

            val c = parser.parseCoordinate(jar)
            assertThat(c!!.groupId).isEqualTo("com.bftcom")
            assertThat(c.artifactId).isEqualTo("my-lib")
            assertThat(c.version).isEqualTo("1.2.3")
        }

        @Test
        fun `многосегментный groupId из Maven-пути`(@TempDir dir: Path) {
            val jarPath = dir.resolve(".m2")
                .resolve("repository").resolve("com").resolve("bftcom").resolve("platform")
                .resolve("platform-core").resolve("5.0.1")
                .resolve("platform-core-5.0.1.jar")

            Files.createDirectories(jarPath.parent)
            val jar = TestJarUtils.emptyJar(jarPath)

            val c = parser.parseCoordinate(jar)
            assertThat(c!!.groupId).isEqualTo("com.bftcom.platform")
            assertThat(c.artifactId).isEqualTo("platform-core")
            assertThat(c.version).isEqualTo("5.0.1")
        }
    }

    // ============================================================
    // Strategy 8: Gradle project (build/libs/)
    // ============================================================
    @Nested
    inner class GradleProjectStrategy {

        @Test
        fun `читает group из gradle_properties`(@TempDir dir: Path) {
            // project/build/libs/my-app-1.0.0.jar
            val projectDir = dir.resolve("project")
            val buildLibsDir = projectDir.resolve("build").resolve("libs")
            Files.createDirectories(buildLibsDir)

            val jar = TestJarUtils.emptyJar(buildLibsDir.resolve("my-app-1.0.0.jar"))

            // gradle.properties в корне проекта
            Files.writeString(
                projectDir.resolve("gradle.properties"),
                "group=com.bftcom\nversion=1.0.0\n",
            )
            // settings.gradle чтобы пометить корень
            Files.writeString(projectDir.resolve("settings.gradle.kts"), "rootProject.name = \"my-app\"")

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom")
            assertThat(c.artifactId).isEqualTo("my-app")
            assertThat(c.version).isEqualTo("1.0.0")
        }

        @Test
        fun `читает group из build_gradle_kts`(@TempDir dir: Path) {
            val projectDir = dir.resolve("project2")
            val buildLibsDir = projectDir.resolve("build").resolve("libs")
            Files.createDirectories(buildLibsDir)

            val jar = TestJarUtils.emptyJar(buildLibsDir.resolve("service-2.0.0.jar"))

            Files.writeString(
                projectDir.resolve("build.gradle.kts"),
                """
                    plugins { kotlin("jvm") }
                    group = "ru.bftcom"
                    version = "2.0.0"
                """.trimIndent(),
            )
            Files.writeString(projectDir.resolve("settings.gradle.kts"), "")

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("ru.bftcom")
            assertThat(c.artifactId).isEqualTo("service")
            assertThat(c.version).isEqualTo("2.0.0")
        }

        @Test
        fun `читает group из build_gradle (Groovy)`(@TempDir dir: Path) {
            val projectDir = dir.resolve("project3")
            val buildLibsDir = projectDir.resolve("build").resolve("libs")
            Files.createDirectories(buildLibsDir)

            val jar = TestJarUtils.emptyJar(buildLibsDir.resolve("api-3.0.0.jar"))

            Files.writeString(
                projectDir.resolve("build.gradle"),
                """
                    apply plugin: 'java'
                    group 'com.bftcom.platform'
                    version '3.0.0'
                """.trimIndent(),
            )
            Files.writeString(projectDir.resolve("settings.gradle"), "")

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom.platform")
            assertThat(c.artifactId).isEqualTo("api")
        }

        @Test
        fun `JAR без версии в имени — version = unspecified`(@TempDir dir: Path) {
            val projectDir = dir.resolve("project4")
            val buildLibsDir = projectDir.resolve("build").resolve("libs")
            Files.createDirectories(buildLibsDir)

            val jar = TestJarUtils.emptyJar(buildLibsDir.resolve("my-module.jar"))

            Files.writeString(
                projectDir.resolve("gradle.properties"),
                "group=com.bftcom\n",
            )
            Files.writeString(projectDir.resolve("settings.gradle.kts"), "")

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom")
            assertThat(c.artifactId).isEqualTo("my-module")
            assertThat(c.version).isEqualTo("unspecified")
        }

        @Test
        fun `ищет group в родительской директории (multi-module)`(@TempDir dir: Path) {
            // root-project/submodule/build/libs/submodule-1.0.0.jar
            val rootDir = dir.resolve("root-project")
            val submoduleDir = rootDir.resolve("submodule")
            val buildLibsDir = submoduleDir.resolve("build").resolve("libs")
            Files.createDirectories(buildLibsDir)

            val jar = TestJarUtils.emptyJar(buildLibsDir.resolve("submodule-1.0.0.jar"))

            // group определён в корневом gradle.properties
            Files.writeString(
                rootDir.resolve("gradle.properties"),
                "group=com.bftcom\n",
            )
            Files.writeString(rootDir.resolve("settings.gradle.kts"), "include(\"submodule\")")

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom")
            assertThat(c.artifactId).isEqualTo("submodule")
        }
    }

    // ============================================================
    // Strategy 9: Package scanning
    // ============================================================
    @Nested
    inner class PackageScanStrategy {

        @Test
        fun `определяет groupId по общему префиксу пакетов`(@TempDir dir: Path) {
            val jarPath = dir.resolve("unknown-lib-1.0.0.jar")

            // Создаём JAR с классами в пакете com.bftcom.mylib
            val class1 = generateMinimalClass("com/bftcom/mylib/service/ServiceA")
            val class2 = generateMinimalClass("com/bftcom/mylib/model/ModelB")
            val class3 = generateMinimalClass("com/bftcom/mylib/util/UtilC")

            val jar = TestJarUtils.writeJar(
                jarPath,
                mapOf(
                    "com/bftcom/mylib/service/ServiceA.class" to class1,
                    "com/bftcom/mylib/model/ModelB.class" to class2,
                    "com/bftcom/mylib/util/UtilC.class" to class3,
                ),
            )

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom.mylib")
            assertThat(c.artifactId).isEqualTo("unknown-lib")
            assertThat(c.version).isEqualTo("1.0.0")
        }

        @Test
        fun `общий префикс из двух сегментов`(@TempDir dir: Path) {
            val jarPath = dir.resolve("lib-2.0.0.jar")

            val class1 = generateMinimalClass("ru/bftcom/moduleA/Foo")
            val class2 = generateMinimalClass("ru/bftcom/moduleB/Bar")

            val jar = TestJarUtils.writeJar(
                jarPath,
                mapOf(
                    "ru/bftcom/moduleA/Foo.class" to class1,
                    "ru/bftcom/moduleB/Bar.class" to class2,
                ),
            )

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("ru.bftcom")
        }

        @Test
        fun `пустой JAR без классов — null`(@TempDir dir: Path) {
            val jarPath = dir.resolve("empty-1.0.0.jar")
            val jar = TestJarUtils.emptyJar(jarPath)

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNull()
        }

        @Test
        fun `один пакет — используется как groupId`(@TempDir dir: Path) {
            val jarPath = dir.resolve("single-pkg-1.0.jar")

            val classBytes = generateMinimalClass("com/bftcom/special/Tool")

            val jar = TestJarUtils.writeJar(
                jarPath,
                mapOf("com/bftcom/special/Tool.class" to classBytes),
            )

            val c = parser.parseCoordinate(jar)
            assertThat(c).isNotNull
            assertThat(c!!.groupId).isEqualTo("com.bftcom.special")
        }
    }

    // ============================================================
    // Общие кейсы
    // ============================================================
    @Nested
    inner class GeneralCases {

        @Test
        fun `non-jar returns null`(@TempDir dir: Path) {
            val f = dir.resolve("x.txt").toFile().apply { writeText("x") }
            assertThat(parser.parseCoordinate(f)).isNull()
        }

        @Test
        fun `несуществующий файл — null`() {
            val f = File("/nonexistent/path/to/lib.jar")
            assertThat(parser.parseCoordinate(f)).isNull()
        }

        @Test
        fun `JAR без каких-либо метаданных и без классов — null`(@TempDir dir: Path) {
            val jarPath = dir.resolve("mystery.jar")
            val jar = TestJarUtils.emptyJar(jarPath)
            assertThat(parser.parseCoordinate(jar)).isNull()
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    /**
     * Создаёт JAR с указанными атрибутами MANIFEST.MF.
     */
    private fun writeJarWithManifest(
        jarPath: Path,
        configureAttrs: (Attributes) -> Unit,
    ): File {
        Files.createDirectories(jarPath.parent)
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            configureAttrs(mainAttributes)
        }
        JarOutputStream(Files.newOutputStream(jarPath), manifest).use { /* empty JAR */ }
        return jarPath.toFile()
    }

    /**
     * Генерирует минимальный .class файл для заданного internal name.
     */
    private fun generateMinimalClass(internalName: String): ByteArray {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null)
        cw.visitEnd()
        return cw.toByteArray()
    }
}
