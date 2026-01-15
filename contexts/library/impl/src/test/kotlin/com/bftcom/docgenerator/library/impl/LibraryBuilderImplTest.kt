package com.bftcom.docgenerator.library.impl

import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.db.LibraryRepository
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.library.Library
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.library.api.BytecodeParser
import com.bftcom.docgenerator.library.api.LibraryCoordinate
import com.bftcom.docgenerator.library.api.RawLibraryNode
import com.bftcom.docgenerator.library.api.bytecode.BytecodeAnalysisResult
import com.bftcom.docgenerator.library.api.bytecode.CallGraph
import com.bftcom.docgenerator.library.api.bytecode.MethodId
import com.bftcom.docgenerator.library.api.bytecode.MethodSummary
import com.bftcom.docgenerator.library.api.bytecode.HttpBytecodeAnalyzer
import com.bftcom.docgenerator.library.impl.coordinate.LibraryCoordinateParser
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class LibraryBuilderImplTest {
    @Test
    fun `buildLibraries - агрегирует результаты и собирает ошибки`(@TempDir dir: Path) {
        val jar1 = dir.resolve("a.jar").toFile().apply { writeBytes(byteArrayOf()) }
        val jar2 = dir.resolve("b.jar").toFile().apply { writeBytes(byteArrayOf()) }
        val nonJar = dir.resolve("c.txt").toFile().apply { writeText("x") }

        val self = mockk<LibraryBuilderImpl>()
        every { self.processSingleLibrary(jar1) } returns LibraryBuilderImpl.SingleLibraryResult(librariesProcessed = 1, librariesSkipped = 0, nodesCreated = 2)
        every { self.processSingleLibrary(jar2) } throws RuntimeException("boom")

        val builder =
            LibraryBuilderImpl(
                coordinateParser = mockk(relaxed = true),
                bytecodeParser = mockk(relaxed = true),
                httpBytecodeAnalyzer = mockk(relaxed = true),
                libraryRepo = mockk(relaxed = true),
                libraryNodeRepo = mockk(relaxed = true),
                objectMapper = ObjectMapper().registerKotlinModule(),
                self = self,
            )

        val res = builder.buildLibraries(listOf(nonJar, jar1, jar2))
        assertThat(res.librariesProcessed).isEqualTo(1)
        assertThat(res.nodesCreated).isEqualTo(2)
        assertThat(res.errors).hasSize(1)
    }

    @Test
    fun `processSingleLibrary - пропускает jar без координат`(@TempDir dir: Path) {
        val jar = dir.resolve("x.jar").toFile().apply { writeBytes(byteArrayOf()) }

        val coordinateParser = mockk<LibraryCoordinateParser>()
        every { coordinateParser.parseCoordinate(jar) } returns null

        val builder =
            LibraryBuilderImpl(
                coordinateParser = coordinateParser,
                bytecodeParser = mockk(relaxed = true),
                httpBytecodeAnalyzer = mockk(relaxed = true),
                libraryRepo = mockk(relaxed = true),
                libraryNodeRepo = mockk(relaxed = true),
                objectMapper = ObjectMapper().registerKotlinModule(),
                self = null,
            )

        val res = builder.processSingleLibrary(jar)
        assertThat(res.librariesProcessed).isEqualTo(0)
        assertThat(res.librariesSkipped).isEqualTo(0)
        assertThat(res.nodesCreated).isEqualTo(0)
    }

    @Test
    fun `processSingleLibrary - пропускает внешнюю библиотеку (не company)`(@TempDir dir: Path) {
        val jar = dir.resolve("x.jar").toFile().apply { writeBytes(byteArrayOf()) }

        val coordinateParser = mockk<LibraryCoordinateParser>()
        every { coordinateParser.parseCoordinate(jar) } returns LibraryCoordinate("org.apache", "x", "1.0")

        val builder =
            LibraryBuilderImpl(
                coordinateParser = coordinateParser,
                bytecodeParser = mockk(relaxed = true),
                httpBytecodeAnalyzer = mockk(relaxed = true),
                libraryRepo = mockk(relaxed = true),
                libraryNodeRepo = mockk(relaxed = true),
                objectMapper = ObjectMapper().registerKotlinModule(),
                self = null,
            )

        val res = builder.processSingleLibrary(jar)
        assertThat(res.librariesProcessed).isEqualTo(0)
        assertThat(res.librariesSkipped).isEqualTo(1)
        assertThat(res.nodesCreated).isEqualTo(0)
    }

    @Test
    fun `processSingleLibrary - создает library и nodes на минимальном наборе rawNodes`(@TempDir dir: Path) {
        val jar = dir.resolve("x.jar").toFile().apply { writeBytes(byteArrayOf()) }

        val coordinate = LibraryCoordinate("com.bftcom", "lib", "1.0")
        val coordinateParser = mockk<LibraryCoordinateParser>()
        every { coordinateParser.parseCoordinate(jar) } returns coordinate

        val rawClass =
            RawLibraryNode(
                fqn = "com.example.Client",
                name = "Client",
                packageName = "com.example",
                kind = NodeKind.CLASS,
                lang = Lang.java,
                filePath = "com/example/Client.class",
                signature = null,
                annotations = listOf("Service"),
                modifiers = setOf("public"),
                parentFqn = null,
                meta = emptyMap(),
            )
        val rawMethod =
            RawLibraryNode(
                fqn = "com.example.Client.call",
                name = "call",
                packageName = "com.example",
                kind = NodeKind.METHOD,
                lang = Lang.java,
                filePath = "com/example/Client.class",
                signature = "void()",
                annotations = emptyList(),
                modifiers = setOf("public"),
                parentFqn = "com.example.Client",
                meta = emptyMap(),
            )

        val bytecodeParser = mockk<BytecodeParser>()
        every { bytecodeParser.parseJar(jar) } returns listOf(rawClass, rawMethod)

        val methodId = MethodId(owner = "com/example/Client", name = "call", descriptor = "()V")
        val summary = MethodSummary(methodId = methodId, urls = setOf("/api"), httpMethods = setOf("GET"), hasRetry = true)
        val analysis =
            BytecodeAnalysisResult(
                callGraph = CallGraph(calls = mapOf(methodId to emptySet())),
                httpCallSites = emptyList(),
                kafkaCallSites = emptyList(),
                camelCallSites = emptyList(),
                methodSummaries = mapOf(methodId to summary),
                parentClients = emptySet(),
            )

        val httpAnalyzer = mockk<HttpBytecodeAnalyzer>()
        every { httpAnalyzer.analyzeJar(jar) } returns analysis

        val libraryRepo = mockk<LibraryRepository>()
        every { libraryRepo.findByCoordinate(any()) } returns null
        every { libraryRepo.save(any()) } answers {
            val lib = firstArg<Library>()
            lib.id = 1L
            lib
        }

        val nodeRepo = mockk<LibraryNodeRepository>()
        every { nodeRepo.findByLibraryIdAndFqn(1L, any()) } returns null
        every { nodeRepo.save(any()) } answers { firstArg<LibraryNode>() }

        val builder =
            LibraryBuilderImpl(
                coordinateParser = coordinateParser,
                bytecodeParser = bytecodeParser,
                httpBytecodeAnalyzer = httpAnalyzer,
                libraryRepo = libraryRepo,
                libraryNodeRepo = nodeRepo,
                objectMapper = ObjectMapper().registerKotlinModule(),
                self = null,
            )

        val res = builder.processSingleLibrary(jar)
        assertThat(res.librariesProcessed).isEqualTo(1)
        assertThat(res.librariesSkipped).isEqualTo(0)
        assertThat(res.nodesCreated).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `processSingleLibrary - если library уже есть, возвращает skipped`(@TempDir dir: Path) {
        val jar = dir.resolve("x.jar").toFile().apply { writeBytes(byteArrayOf()) }

        val coordinate = LibraryCoordinate("com.bftcom", "lib", "1.0")
        val coordinateParser = mockk<LibraryCoordinateParser>()
        every { coordinateParser.parseCoordinate(jar) } returns coordinate

        val existing = Library(id = 1L, coordinate = coordinate.coordinate, groupId = coordinate.groupId, artifactId = coordinate.artifactId, version = coordinate.version)
        val libraryRepo = mockk<LibraryRepository>()
        every { libraryRepo.findByCoordinate(any()) } returns existing

        val builder =
            LibraryBuilderImpl(
                coordinateParser = coordinateParser,
                bytecodeParser = mockk(relaxed = true),
                httpBytecodeAnalyzer = mockk(relaxed = true),
                libraryRepo = libraryRepo,
                libraryNodeRepo = mockk(relaxed = true),
                objectMapper = ObjectMapper().registerKotlinModule(),
                self = null,
            )

        val res = builder.processSingleLibrary(jar)
        assertThat(res.librariesProcessed).isEqualTo(0)
        assertThat(res.librariesSkipped).isEqualTo(1)
        assertThat(res.nodesCreated).isEqualTo(0)
    }

    @Test
    fun `processSingleLibrary - если анализатор падает, продолжаем без analysisResult`(@TempDir dir: Path) {
        val jar = dir.resolve("x.jar").toFile().apply { writeBytes(byteArrayOf()) }

        val coordinate = LibraryCoordinate("com.bftcom", "lib", "1.0")
        val coordinateParser = mockk<LibraryCoordinateParser>()
        every { coordinateParser.parseCoordinate(jar) } returns coordinate

        val bytecodeParser = mockk<BytecodeParser>()
        every { bytecodeParser.parseJar(jar) } returns
            listOf(
                RawLibraryNode(
                    fqn = "com.example.Client",
                    name = "Client",
                    packageName = "com.example",
                    kind = NodeKind.INTERFACE,
                    lang = Lang.java,
                    filePath = "com/example/Client.class",
                    signature = null,
                    annotations = emptyList(),
                    modifiers = setOf("public"),
                ),
            )

        val httpAnalyzer = mockk<HttpBytecodeAnalyzer>()
        every { httpAnalyzer.analyzeJar(jar) } throws RuntimeException("analyzer down")

        val libraryRepo = mockk<LibraryRepository>()
        every { libraryRepo.findByCoordinate(any()) } returns null
        every { libraryRepo.save(any()) } answers {
            val lib = firstArg<Library>()
            lib.id = 1L
            lib
        }

        val nodeRepo = mockk<LibraryNodeRepository>()
        every { nodeRepo.findByLibraryIdAndFqn(1L, any()) } returns null
        every { nodeRepo.save(any()) } answers { firstArg<LibraryNode>() }

        val builder =
            LibraryBuilderImpl(
                coordinateParser = coordinateParser,
                bytecodeParser = bytecodeParser,
                httpBytecodeAnalyzer = httpAnalyzer,
                libraryRepo = libraryRepo,
                libraryNodeRepo = nodeRepo,
                objectMapper = ObjectMapper().registerKotlinModule(),
                self = null,
            )

        val res = builder.processSingleLibrary(jar)
        assertThat(res.librariesProcessed).isEqualTo(1)
        assertThat(res.nodesCreated).isGreaterThanOrEqualTo(1)
    }
}

