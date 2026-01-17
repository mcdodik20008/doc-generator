package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.application.Application
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class ApplicationRepositoryTest : BaseRepositoryTest() {

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    @Test
    fun `findByKey - возвращает Application по ключу`() {
        // Given
        val application = Application(
            key = "test-app-1",
            name = "Test Application",
            description = "Test Description",
        )
        applicationRepository.save(application)

        // When
        val found = applicationRepository.findByKey("test-app-1")

        // Then
        assertThat(found).isNotNull
        assertThat(found!!.key).isEqualTo("test-app-1")
        assertThat(found.name).isEqualTo("Test Application")
        assertThat(found.description).isEqualTo("Test Description")
    }

    @Test
    fun `findByKey - возвращает null для несуществующего ключа`() {
        // When
        val found = applicationRepository.findByKey("non-existent-key")

        // Then
        assertThat(found).isNull()
    }

    @Test
    fun `save - сохраняет новую Application`() {
        // Given
        val application = Application(
            key = "test-app-2",
            name = "Test Application 2",
        )

        // When
        val saved = applicationRepository.save(application)

        // Then
        assertThat(saved.id).isNotNull
        assertThat(saved.key).isEqualTo("test-app-2")
        assertThat(saved.name).isEqualTo("Test Application 2")
    }

    @Test
    fun `findAll - возвращает все Application`() {
        // Given
        val app1 = Application(key = "app-1", name = "App 1")
        val app2 = Application(key = "app-2", name = "App 2")
        applicationRepository.save(app1)
        applicationRepository.save(app2)

        // When
        val all = applicationRepository.findAll()

        // Then
        assertThat(all).hasSizeGreaterThanOrEqualTo(2)
        assertThat(all.map { it.key }).contains("app-1", "app-2")
    }

    @Test
    fun `findById - возвращает Application по ID`() {
        // Given
        val application = Application(key = "app-3", name = "App 3")
        val saved = applicationRepository.save(application)

        // When
        val found = applicationRepository.findById(saved.id!!)

        // Then
        assertThat(found).isPresent
        assertThat(found.get().key).isEqualTo("app-3")
    }
}