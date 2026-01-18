package com.bftcom.docgenerator.rag.impl.advisors

import com.bftcom.docgenerator.db.ApplicationRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient

class ExactNodeSearchAdvisorTest {

    @Test
    fun `process - пропускает если EXACT_NODES уже присутствует`() {
        val chatClient = mockk<ChatClient>(relaxed = true)
        val nodeRepository = mockk<NodeRepository>(relaxed = true)
        val applicationRepository = mockk<ApplicationRepository>(relaxed = true)
        val objectMapper = mockk<ObjectMapper>(relaxed = true)
        val advisor = ExactNodeSearchAdvisor(chatClient, nodeRepository, applicationRepository, objectMapper)

        var context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "класс UserService метод getUser",
            sessionId = "s-1",
        ).setMetadata(QueryMetadataKeys.EXACT_NODES, listOf(mockk<Node>()))

        val result = advisor.process(context)

        assertThat(result).isTrue
        verify(exactly = 0) { chatClient.prompt() }
        verify(exactly = 0) { applicationRepository.findAll() }
        verify(exactly = 0) { nodeRepository.findByApplicationIdAndClassNameAndMethodName(any(), any(), any(), any()) }
    }

    @Test
    fun `process - использует LLM JSON и сохраняет найденные узлы в метаданные`() {
        val chatClient = mockk<ChatClient>()
        val nodeRepository = mockk<NodeRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val objectMapper = mockk<ObjectMapper>()
        val advisor = ExactNodeSearchAdvisor(chatClient, nodeRepository, applicationRepository, objectMapper)

        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns "```json\n{\"className\":\"UserService\",\"methodName\":\"getUser\"}\n```"

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        @Suppress("UNCHECKED_CAST")
        every { objectMapper.readValue(any<String>(), Map::class.java) } returns mapOf(
            "className" to "UserService",
            "methodName" to "getUser",
        ) as Map<*, *>

        val app = Application(id = 1L, key = "app", name = "App")
        every { applicationRepository.findAll() } returns listOf(app)

        val methodNode = Node(
            id = 10L,
            application = app,
            fqn = "com.example.UserService.getUser",
            name = "getUser",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )
        every {
            nodeRepository.findByApplicationIdAndClassNameAndMethodName(
                applicationId = 1L,
                className = "UserService",
                methodName = "getUser",
                methodKind = NodeKind.METHOD,
            )
        } returns listOf(methodNode)

        var context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "что делает getUser в UserService",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        val foundNodes = context.getMetadata<List<Node>>(QueryMetadataKeys.EXACT_NODES)
        assertThat(foundNodes).isNotNull
        assertThat(foundNodes!!).hasSize(1)
        assertThat(foundNodes[0].id).isEqualTo(10L)

        @Suppress("UNCHECKED_CAST")
        val searchResult = context.getMetadata<Map<String, String>>(QueryMetadataKeys.EXACT_NODE_SEARCH_RESULT)
        assertThat(searchResult).isNotNull
        assertThat(searchResult!!["className"]).isEqualTo("UserService")
        assertThat(searchResult["methodName"]).isEqualTo("getUser")

        assertThat(context.processingSteps).hasSize(1)
        assertThat(context.processingSteps[0].advisorName).isEqualTo("ExactNodeSearch")
        assertThat(context.processingSteps[0].output).contains("Найдено узлов: 1")
    }

    @Test
    fun `process - fallback на Regex когда LLM вернул null`() {
        val chatClient = mockk<ChatClient>()
        val nodeRepository = mockk<NodeRepository>()
        val applicationRepository = mockk<ApplicationRepository>()
        val objectMapper = mockk<ObjectMapper>(relaxed = true)
        val advisor = ExactNodeSearchAdvisor(chatClient, nodeRepository, applicationRepository, objectMapper)

        val callResponse = mockk<ChatClient.CallResponseSpec>()
        every { callResponse.content() } returns null

        val promptSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true, relaxUnitFun = true)
        every { promptSpec.user(any<String>()) } returns promptSpec
        every { promptSpec.call() } returns callResponse
        every { chatClient.prompt() } returns promptSpec

        val app = Application(id = 1L, key = "app", name = "App")
        every { applicationRepository.findAll() } returns listOf(app)

        val methodNode = Node(
            id = 11L,
            application = app,
            fqn = "com.example.UserService.getUser",
            name = "getUser",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )
        every {
            nodeRepository.findByApplicationIdAndClassNameAndMethodName(
                applicationId = 1L,
                className = "UserService",
                methodName = "getUser",
                methodKind = NodeKind.METHOD,
            )
        } returns listOf(methodNode)

        val context = QueryProcessingContext(
            originalQuery = "ignored",
            currentQuery = "класс UserService метод getUser",
            sessionId = "s-1",
        )

        val result = advisor.process(context)

        assertThat(result).isTrue
        assertThat(context.hasMetadata(QueryMetadataKeys.EXACT_NODES)).isTrue
        verify(exactly = 0) { objectMapper.readValue(any<String>(), Map::class.java) }
    }
}

