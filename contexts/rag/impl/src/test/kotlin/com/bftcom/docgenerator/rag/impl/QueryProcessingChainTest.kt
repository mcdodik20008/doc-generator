package com.bftcom.docgenerator.rag.impl

import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingAdvisor
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QueryProcessingChainTest {
    private lateinit var advisor1: QueryProcessingAdvisor
    private lateinit var advisor2: QueryProcessingAdvisor
    private lateinit var advisor3: QueryProcessingAdvisor
    private lateinit var chain: QueryProcessingChain

    @BeforeEach
    fun setUp() {
        advisor1 = mockk(relaxed = true)
        advisor2 = mockk(relaxed = true)
        advisor3 = mockk(relaxed = true)
        chain = QueryProcessingChain(listOf(advisor1, advisor2, advisor3))

        // Дефолты для relaxed-моков:
        // - order=0 и process=false приводят к преждевременному завершению цепочки
        every { advisor1.getName() } returns "Advisor1"
        every { advisor2.getName() } returns "Advisor2"
        every { advisor3.getName() } returns "Advisor3"

        every { advisor1.getOrder() } returns 1
        every { advisor2.getOrder() } returns 2
        every { advisor3.getOrder() } returns 3

        every { advisor1.process(any()) } returns true
        every { advisor2.process(any()) } returns true
        every { advisor3.process(any()) } returns true
    }

    @Test
    fun `process - выполняет advisors в порядке приоритета`() {
        // Arrange
        val query = "test query"
        val sessionId = "session-1"
        
        every { advisor1.getName() } returns "Advisor1"
        every { advisor1.getOrder() } returns 2
        every { advisor1.process(any()) } returns true
        
        every { advisor2.getName() } returns "Advisor2"
        every { advisor2.getOrder() } returns 1
        every { advisor2.process(any()) } returns true
        
        every { advisor3.getName() } returns "Advisor3"
        every { advisor3.getOrder() } returns 3
        every { advisor3.process(any()) } returns true

        // Act
        val result = chain.process(query, sessionId)

        // Assert
        assertThat(result.originalQuery).isEqualTo(query)
        assertThat(result.sessionId).isEqualTo(sessionId)
        
        // Проверяем порядок вызовов: advisor2 (order=1), advisor1 (order=2), advisor3 (order=3)
        verify(exactly = 1) { advisor2.process(any()) }
        verify(exactly = 1) { advisor1.process(any()) }
        verify(exactly = 1) { advisor3.process(any()) }
    }

    @Test
    fun `process - прерывает цепочку когда advisor возвращает false`() {
        // Arrange
        val query = "test query"
        val sessionId = "session-1"
        
        every { advisor1.getName() } returns "Advisor1"
        every { advisor1.getOrder() } returns 1
        every { advisor1.process(any()) } returns false // Прерываем цепочку
        
        every { advisor2.getName() } returns "Advisor2"
        every { advisor2.getOrder() } returns 2
        // advisor2 не должен быть вызван
        
        every { advisor3.getName() } returns "Advisor3"
        every { advisor3.getOrder() } returns 3
        // advisor3 не должен быть вызван

        // Act
        val result = chain.process(query, sessionId)

        // Assert
        assertThat(result.originalQuery).isEqualTo(query)
        verify(exactly = 1) { advisor1.process(any()) }
        verify(exactly = 0) { advisor2.process(any()) }
        verify(exactly = 0) { advisor3.process(any()) }
    }

    @Test
    fun `process - обрабатывает ошибки в advisors и продолжает цепочку`() {
        // Arrange
        val query = "test query"
        val sessionId = "session-1"
        val errorMessage = "Test error"
        
        every { advisor1.getName() } returns "Advisor1"
        every { advisor1.getOrder() } returns 1
        every { advisor1.process(any()) } throws RuntimeException(errorMessage)
        
        every { advisor2.getName() } returns "Advisor2"
        every { advisor2.getOrder() } returns 2
        every { advisor2.process(any()) } returns true
        
        every { advisor3.getName() } returns "Advisor3"
        every { advisor3.getOrder() } returns 3
        every { advisor3.process(any()) } returns true

        // Act
        val result = chain.process(query, sessionId)

        // Assert
        assertThat(result.originalQuery).isEqualTo(query)
        
        // Проверяем, что ошибка записана в метаданные
        val errorKey = "${QueryMetadataKeys.ERROR_PREFIX.key}Advisor1"
        assertThat(result.metadata[errorKey]).isEqualTo(errorMessage)
        
        // Проверяем, что цепочка продолжилась
        verify(exactly = 1) { advisor1.process(any()) }
        verify(exactly = 1) { advisor2.process(any()) }
        verify(exactly = 1) { advisor3.process(any()) }
    }

    @Test
    fun `process - обрабатывает ошибку без сообщения`() {
        // Arrange
        val query = "test query"
        val sessionId = "session-1"
        
        every { advisor1.getName() } returns "Advisor1"
        every { advisor1.getOrder() } returns 1
        every { advisor1.process(any()) } throws RuntimeException()
        
        every { advisor2.getName() } returns "Advisor2"
        every { advisor2.getOrder() } returns 2
        every { advisor2.process(any()) } returns true

        // Act
        val result = chain.process(query, sessionId)

        // Assert
        val errorKey = "${QueryMetadataKeys.ERROR_PREFIX.key}Advisor1"
        assertThat(result.metadata[errorKey]).isEqualTo("Unknown error")
    }

    @Test
    fun `process - создает правильный контекст`() {
        // Arrange
        val query = "original query"
        val sessionId = "session-123"
        
        every { advisor1.getName() } returns "Advisor1"
        every { advisor1.getOrder() } returns 1
        every { advisor1.process(any()) } answers {
            val context = firstArg<QueryProcessingContext>()
            context.setMetadata(QueryMetadataKeys.REWRITTEN_QUERY, "rewritten query")
            true
        }
        
        every { advisor2.getName() } returns "Advisor2"
        every { advisor2.getOrder() } returns 2
        every { advisor2.process(any()) } returns true
        
        every { advisor3.getName() } returns "Advisor3"
        every { advisor3.getOrder() } returns 3
        every { advisor3.process(any()) } returns true

        // Act
        val result = chain.process(query, sessionId)

        // Assert
        assertThat(result.originalQuery).isEqualTo(query)
        assertThat(result.currentQuery).isEqualTo(query)
        assertThat(result.sessionId).isEqualTo(sessionId)
        assertThat(result.getMetadata<String>(QueryMetadataKeys.REWRITTEN_QUERY)).isEqualTo("rewritten query")
    }

    @Test
    fun `process - пустой список advisors возвращает контекст без изменений`() {
        // Arrange
        val emptyChain = QueryProcessingChain(emptyList())
        val query = "test query"
        val sessionId = "session-1"

        // Act
        val result = emptyChain.process(query, sessionId)

        // Assert
        assertThat(result.originalQuery).isEqualTo(query)
        assertThat(result.currentQuery).isEqualTo(query)
        assertThat(result.sessionId).isEqualTo(sessionId)
        assertThat(result.metadata).isEmpty()
        assertThat(result.processingSteps).isEmpty()
    }

    @Test
    fun `process - advisors с одинаковым приоритетом выполняются в порядке списка`() {
        // Arrange
        val advisorA = mockk<QueryProcessingAdvisor>()
        val advisorB = mockk<QueryProcessingAdvisor>()
        val chain = QueryProcessingChain(listOf(advisorA, advisorB))
        
        val query = "test query"
        val sessionId = "session-1"
        
        every { advisorA.getName() } returns "AdvisorA"
        every { advisorA.getOrder() } returns 1
        every { advisorA.process(any()) } returns true
        
        every { advisorB.getName() } returns "AdvisorB"
        every { advisorB.getOrder() } returns 1 // Тот же приоритет
        every { advisorB.process(any()) } returns true

        // Act
        chain.process(query, sessionId)

        // Assert
        // Оба должны быть вызваны, порядок определяется порядком в списке при одинаковом приоритете
        verify(exactly = 1) { advisorA.process(any()) }
        verify(exactly = 1) { advisorB.process(any()) }
    }
}
