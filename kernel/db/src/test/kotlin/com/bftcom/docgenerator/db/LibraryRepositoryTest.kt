package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.library.Library
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class LibraryRepositoryTest : BaseRepositoryTest() {

    @Autowired
    private lateinit var libraryRepository: LibraryRepository

    @Test
    fun `findByCoordinate - возвращает Library по координату`() {
        // Given
        val library = Library(
            coordinate = "org.springframework:spring-webflux:6.1.0",
            groupId = "org.springframework",
            artifactId = "spring-webflux",
            version = "6.1.0",
            kind = "external",
        )
        libraryRepository.save(library)

        // When
        val found = libraryRepository.findByCoordinate("org.springframework:spring-webflux:6.1.0")

        // Then
        assertThat(found).isNotNull
        assertThat(found!!.coordinate).isEqualTo("org.springframework:spring-webflux:6.1.0")
        assertThat(found.groupId).isEqualTo("org.springframework")
        assertThat(found.artifactId).isEqualTo("spring-webflux")
        assertThat(found.version).isEqualTo("6.1.0")
        assertThat(found.kind).isEqualTo("external")
    }

    @Test
    fun `findByCoordinate - возвращает null для несуществующего координата`() {
        // When
        val found = libraryRepository.findByCoordinate("non:existent:1.0.0")

        // Then
        assertThat(found).isNull()
    }

    @Test
    fun `save - сохраняет новую Library`() {
        // Given
        val library = Library(
            coordinate = "com.example:test-lib:1.0.0",
            groupId = "com.example",
            artifactId = "test-lib",
            version = "1.0.0",
            metadata = mapOf("license" to "MIT", "url" to "https://example.com"),
        )

        // When
        val saved = libraryRepository.save(library)

        // Then
        assertThat(saved.id).isNotNull
        assertThat(saved.coordinate).isEqualTo("com.example:test-lib:1.0.0")
        assertThat(saved.metadata).containsEntry("license", "MIT")
        assertThat(saved.metadata).containsEntry("url", "https://example.com")
    }

    @Test
    fun `findAll - возвращает все Library`() {
        // Given
        val lib1 = Library(
            coordinate = "lib-1:artifact:1.0.0",
            groupId = "lib-1",
            artifactId = "artifact",
            version = "1.0.0",
        )
        val lib2 = Library(
            coordinate = "lib-2:artifact:2.0.0",
            groupId = "lib-2",
            artifactId = "artifact",
            version = "2.0.0",
        )
        libraryRepository.save(lib1)
        libraryRepository.save(lib2)

        // When
        val all = libraryRepository.findAll()

        // Then
        assertThat(all).hasSizeGreaterThanOrEqualTo(2)
        assertThat(all.map { it.coordinate }).contains("lib-1:artifact:1.0.0", "lib-2:artifact:2.0.0")
    }
}